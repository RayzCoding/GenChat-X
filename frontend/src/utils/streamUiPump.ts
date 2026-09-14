/**
 * Drain stream chunks onto the UI across animation frames so React can paint
 * incrementally even when the network delivers a large buffered burst.
 */
export function createStreamUiPump<T>(apply: (batch: T[]) => void) {
  const queue: T[] = []
  let raf = 0
  const idleWaiters: Array<() => void> = []

  const notifyIdle = () => {
    if (queue.length > 0 || raf !== 0) return
    const waiters = idleWaiters.splice(0)
    waiters.forEach((resolve) => resolve())
  }

  const nextBatchSize = () => {
    if (queue.length > 80) return 16
    if (queue.length > 24) return 4
    return 1
  }

  const drain = () => {
    raf = 0
    if (queue.length === 0) {
      notifyIdle()
      return
    }
    apply(queue.splice(0, nextBatchSize()))
    if (queue.length > 0) {
      raf = requestAnimationFrame(drain)
    } else {
      notifyIdle()
    }
  }

  return {
    push(item: T) {
      queue.push(item)
      if (raf === 0) {
        raf = requestAnimationFrame(drain)
      }
    },
    flush() {
      if (raf !== 0) {
        cancelAnimationFrame(raf)
        raf = 0
      }
      if (queue.length > 0) {
        apply(queue.splice(0, queue.length))
      }
      notifyIdle()
    },
    whenIdle() {
      if (queue.length === 0 && raf === 0) {
        return Promise.resolve()
      }
      return new Promise<void>((resolve) => {
        idleWaiters.push(resolve)
      })
    },
  }
}
