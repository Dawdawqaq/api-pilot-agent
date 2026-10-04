"""仅备份和恢复本地 DocHelper 数据库与 Bucket，不启动任何业务任务。"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from datetime import datetime


def file_hash(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(65536), b''):
            digest.update(chunk)
    return digest.hexdigest()


def object_tool(root, action, archive):
    command = ['java', '-Dfile.encoding=UTF-8', '-Dsun.stdout.encoding=UTF-8', '-Dsun.stderr.encoding=UTF-8',
               '-Dloader.main=com.dochelper.maintenance.cli.ObjectArchiveTool',
               '-cp', str(root / 'target/dochelper-0.0.1-SNAPSHOT.jar'),
               'org.springframework.boot.loader.launch.PropertiesLauncher', action, str(archive)]
    subprocess.run(command, check=True, cwd=root)


def database_tool(docker, sql_file, restore=False):
    # 密码走标准输入，不放在命令参数和日志里；账号限制为 DocHelper 自己的用户。
    username = os.environ.get('DB_USERNAME', 'dochelper')
    if username != 'dochelper':
        raise ValueError('本地备份固定使用 dochelper 账号，拒绝其他数据库账号')
    password = os.environ['DB_PASSWORD']
    if '\n' in password or '\r' in password:
        raise ValueError('密码不能包含换行')
    operation = ('mysql --user=dochelper --default-character-set=utf8mb4 dochelper' if restore else
                 'mysqldump --user=dochelper --single-transaction --quick --hex-blob '
                 '--no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 dochelper')
    container = os.environ.get('DOCHELPER_MYSQL_CONTAINER', 'dev-infra-mysql-1')
    command = [docker, 'exec', '-i', container, 'sh', '-c',
               'read -r task_password; export MYSQL_PWD="$task_password"; exec ' + operation]
    with sql_file.open('rb' if restore else 'wb') as stream:
        process = subprocess.Popen(command, stdin=subprocess.PIPE,
                                   stdout=subprocess.DEVNULL if restore else stream, stderr=subprocess.PIPE)
        try:
            process.stdin.write((password + '\n').encode('utf-8'))
            if restore:
                for chunk in iter(lambda: stream.read(65536), b''):
                    process.stdin.write(chunk)
                # Qdrant 不属于此备份；恢复后禁止读取旧向量，直到显式重建成功。
                process.stdin.write(b"\nINSERT INTO sys_setting(id,config_key,config_value,description) "
                                    b"SELECT COALESCE(MAX(id),0)+1,'knowledge.index.restore.required','true','restoration' FROM sys_setting "
                                    b"ON DUPLICATE KEY UPDATE config_value='true';\n")
            process.stdin.close()
            stderr = process.stderr.read()
            if process.wait() != 0:
                raise RuntimeError('DocHelper 数据库操作失败，请检查 dochelper 账号权限和 MySQL 状态')
        finally:
            if process.poll() is None:
                process.kill()
            process.stderr.close()


def validate(root, directory):
    manifest = json.loads((directory / 'manifest.json').read_text(encoding='utf-8-sig'))
    if manifest.get('database') != 'dochelper' or manifest.get('bucket') != 'dochelper-files' or manifest.get('version') != 1:
        raise ValueError('备份范围不合法')
    expected = {'database.sql', 'objects.zip', 'master-key.dpapi'}
    if set(manifest.get('files', {})) != expected:
        raise ValueError('备份清单不完整')
    for name, digest in manifest['files'].items():
        if file_hash(directory / name) != digest:
            raise ValueError('备份校验失败：' + name)
    object_tool(root, 'verify', directory / 'objects.zip')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['backup', 'verify', 'restore'])
    parser.add_argument('--root', required=True)
    parser.add_argument('--docker', required=True)
    parser.add_argument('--path')
    parser.add_argument('--confirmation')
    args = parser.parse_args()
    root = Path(args.root).resolve()
    backup_root = root / '.local-notes/backups'
    if args.action == 'backup':
        directory = backup_root / (datetime.now().strftime('%Y%m%d-%H%M%S-%f') + '-pending')
        directory.mkdir(parents=True, exist_ok=False)
        # Windows 包装脚本提前按当前用户加密实际生效的主密钥。
        protected = os.environ['DOCHELPER_BACKUP_PROTECTED_KEY']
        (directory / 'master-key.dpapi').write_text(protected, encoding='utf-8')
        database_tool(args.docker, directory / 'database.sql')
        object_tool(root, 'backup', directory / 'objects.zip')
        manifest = {'version': 1, 'database': 'dochelper', 'bucket': 'dochelper-files',
                    'createdAt': datetime.now().isoformat(), 'files': {
                        name: file_hash(directory / name) for name in ['database.sql', 'objects.zip', 'master-key.dpapi']}}
        (directory / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
        validate(root, directory)
        destination = directory.with_name(directory.name.removesuffix('-pending'))
        directory.rename(destination)
        print('完整备份已保存：' + str(destination))
    else:
        if not args.path:
            raise ValueError('请提供 --path')
        directory = Path(args.path).resolve()
        validate(root, directory)
        if args.action == 'verify':
            print('DocHelper 数据库、对象与密钥文件完整性校验通过')
            return
        if args.confirmation != 'dochelper':
            raise ValueError('恢复须明确确认 dochelper，将替换其数据库内容')
        object_tool(root, 'restore', directory / 'objects.zip')
        database_tool(args.docker, directory / 'database.sql', restore=True)
        # 包装脚本已经验证该 DPAPI 文件能被当前 Windows 用户解密。
        key_path = root / '.local-notes/secret-store-master-key.dpapi'
        key_path.write_bytes((directory / 'master-key.dpapi').read_bytes())
        print('DocHelper 备份已恢复；请启动后显式重建知识库索引。')


if __name__ == '__main__':
    try:
        main()
    except Exception as exception:
        # 不输出子进程请求参数或凭据；保留有意生成的操作说明。
        print(str(exception) if isinstance(exception, (ValueError, RuntimeError)) else
              '备份操作失败，请检查本地文件、Java、Docker 与 MinIO。', file=sys.stderr)
        sys.exit(1)
