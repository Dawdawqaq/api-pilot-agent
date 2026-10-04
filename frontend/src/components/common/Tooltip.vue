<script setup>
import { ref, useId, onMounted, onUnmounted, nextTick } from 'vue'
const props = defineProps({content:{type:String,required:true},position:{type:String,default:'top'}})
const visible = ref(false), wrapper = ref(null), bubble = ref(null)
const tooltipId = useId(), coordinates = ref({left:'0px',top:'0px'})
const hide = () => { visible.value = false }
const show = async () => {
  visible.value = true
  await nextTick()
  if (!wrapper.value || !bubble.value) return
  const anchor = wrapper.value.getBoundingClientRect(), panel = bubble.value.getBoundingClientRect()
  let x = anchor.left + (anchor.width-panel.width)/2
  let y = props.position === 'bottom' ? anchor.bottom+8 : anchor.top-panel.height-8
  if (props.position === 'left') { x=anchor.left-panel.width-8; y=anchor.top+(anchor.height-panel.height)/2 }
  if (props.position === 'right') { x=anchor.right+8; y=anchor.top+(anchor.height-panel.height)/2 }
  coordinates.value = {left:`${Math.max(8,Math.min(x,innerWidth-panel.width-8))}px`,top:`${Math.max(8,Math.min(y,innerHeight-panel.height-8))}px`}
}
onMounted(() => {
  const control = wrapper.value?.querySelector('button,[role="combobox"],input,a')
  if (control) {
    control.setAttribute('aria-describedby',[control.getAttribute('aria-describedby'),tooltipId].filter(Boolean).join(' '))
    if (!control.getAttribute('aria-label') && !control.textContent.trim()) control.setAttribute('aria-label',props.content)
  }
  window.addEventListener('resize',hide)
  window.addEventListener('scroll',hide,true)
})
onUnmounted(() => { window.removeEventListener('resize',hide); window.removeEventListener('scroll',hide,true) })
</script>
<template>
  <div ref="wrapper" class="relative inline-flex items-center" @mouseenter="show" @mouseleave="hide" @focusin="show" @focusout="hide" @keydown.esc="hide">
    <slot />
    <teleport to="body"><div v-show="visible && content" ref="bubble" :id="tooltipId" role="tooltip" :style="coordinates" class="fixed z-[100] pointer-events-none w-max max-w-[280px] px-3 py-2 text-xs font-medium text-white bg-zinc-900 dark:bg-zinc-800 rounded-lg shadow-lg border border-zinc-700/50 leading-relaxed">{{ content }}</div></teleport>
  </div>
</template>
