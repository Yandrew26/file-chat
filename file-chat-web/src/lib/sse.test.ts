import { describe, expect, it } from 'vitest'
import { SseParser } from './sse'

describe('SseParser', () => {
  it('parses named events in the format Spring emits', () => {
    const parser = new SseParser()
    const events = parser.push('event:meta\ndata:{"traceId":"t1"}\n\nevent:token\ndata:{"text":"Hi"}\n\n')
    expect(events).toEqual([
      { event: 'meta', data: '{"traceId":"t1"}' },
      { event: 'token', data: '{"text":"Hi"}' },
    ])
  })

  it('buffers events split across chunks', () => {
    const parser = new SseParser()
    expect(parser.push('event:tok')).toEqual([])
    expect(parser.push('en\ndata:{"text":"a')).toEqual([])
    expect(parser.push('b"}\n')).toEqual([])
    expect(parser.push('\n')).toEqual([{ event: 'token', data: '{"text":"ab"}' }])
  })

  it('joins multi-line data, strips one leading space and ignores comments', () => {
    const parser = new SseParser()
    const events = parser.push(': keep-alive\ndata: line one\ndata:line two\n\n')
    expect(events).toEqual([{ event: 'message', data: 'line one\nline two' }])
  })

  it('handles CRLF line endings and flushes a trailing event', () => {
    const parser = new SseParser()
    expect(parser.push('event:done\r\ndata:{}\r\n\r\nevent:error\r\ndata:{"message":"x"}')).toEqual([
      { event: 'done', data: '{}' },
    ])
    expect(parser.flush()).toEqual([{ event: 'error', data: '{"message":"x"}' }])
  })
})
