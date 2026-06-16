/**
 * SSE 流式请求封装：解析 data: 行，支持 fetch ReadableStream
 * 用于 AI 问答流式响应
 */
export function useSSE() {
  function connect(url, options, onMessage, onDone, onError) {
    const reader = url.body?.getReader()
    const decoder = new TextDecoder()

    if (!reader) {
      // fetch Response
      readStream()
    }

    async function readStream() {
      try {
        // eslint-disable-next-line no-constant-condition
        while (true) {
          const { done, value } = await reader.read()
          if (done) {
            onDone?.()
            break
          }
          const text = decoder.decode(value, { stream: true })
          // 解析 SSE 格式
          const lines = text.split('\n')
          for (const line of lines) {
            if (line.startsWith('data:')) {
              const data = line.slice(5).trim()
              if (data === '[DONE]') {
                onDone?.()
                return
              }
              onMessage?.(data)
            }
          }
        }
      } catch (e) {
        onError?.(e)
      }
    }

    return { readStream }
  }

  async function startFetch(fetchPromise, onMessage, onDone, onError) {
    try {
      const response = await fetchPromise
      if (!response.ok) {
        onError?.(new Error(`HTTP ${response.status}`))
        return
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder()

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          onDone?.()
          break
        }
        const text = decoder.decode(value, { stream: true })
        const lines = text.split('\n')
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5).trim()
            if (data === '[DONE]') {
              onDone?.()
              return
            }
            onMessage?.(data)
          }
        }
      }
    } catch (e) {
      onError?.(e)
    }
  }

  return { connect, startFetch }
}
