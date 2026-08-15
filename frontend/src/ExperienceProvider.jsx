import { useEffect } from 'react'
import Lenis from 'lenis'
import { MotionConfig } from 'motion/react'

export default function ExperienceProvider({ children }) {
  useEffect(() => {
    const lenis = new Lenis({
      autoRaf: true,
      anchors: true,
      prevent: (node) => Boolean(node?.closest?.('[data-lenis-prevent]')),
    })
    return () => lenis.destroy()
  }, [])

  return <MotionConfig reducedMotion="user">{children}</MotionConfig>
}
