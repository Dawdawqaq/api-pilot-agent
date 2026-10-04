import { watch, nextTick, onUnmounted } from 'vue'

const dialogs = []
const focusable = '[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex="0"]'

// 对话框独立管理焦点，关闭后回到开启它的控件。
export function useDialog(isOpen, element, close) {
  const token = {}
  let previous = null
  const remove = () => {
    const index = dialogs.indexOf(token)
    if (index >= 0) dialogs.splice(index, 1)
    document.removeEventListener('keydown', handleKey)
    if (index >= 0 && previous?.isConnected) previous.focus()
  }
  const handleKey = event => {
    if (dialogs.at(-1) !== token) return
    if (event.key === 'Escape') { event.preventDefault(); close(); return }
    if (event.key !== 'Tab') return
    const controls = [...(element.value?.querySelectorAll(focusable) || [])].filter(control => control.getClientRects().length)
    if (!controls.length) { event.preventDefault(); element.value?.focus(); return }
    const first = controls[0], last = controls.at(-1)
    if (event.shiftKey && (document.activeElement === first || !element.value.contains(document.activeElement))) { event.preventDefault(); last.focus() }
    else if (!event.shiftKey && (document.activeElement === last || !element.value.contains(document.activeElement))) { event.preventDefault(); first.focus() }
  }
  watch(isOpen, async open => {
    if (!open) { remove(); return }
    previous = document.activeElement
    dialogs.push(token)
    document.addEventListener('keydown', handleKey)
    await nextTick()
    element.value?.querySelector(focusable)?.focus()
  })
  onUnmounted(remove)
}
