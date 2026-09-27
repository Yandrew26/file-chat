export interface ServerSentEvent {
  event: string
  data: string
}

/**
 * Incremental parser for a text/event-stream body. Feed it decoded chunks as they arrive;
 * it returns the events completed by that chunk and keeps any partial event buffered.
 */
export class SseParser {
  private buffer = ''

  push(chunk: string): ServerSentEvent[] {
    this.buffer += chunk.replace(/\r\n?/g, '\n')
    const events: ServerSentEvent[] = []
    let boundary = this.buffer.indexOf('\n\n')
    while (boundary !== -1) {
      const block = this.buffer.slice(0, boundary)
      this.buffer = this.buffer.slice(boundary + 2)
      const event = parseBlock(block)
      if (event) events.push(event)
      boundary = this.buffer.indexOf('\n\n')
    }
    return events
  }

  /** Returns the final event if the stream ended without a trailing blank line. */
  flush(): ServerSentEvent[] {
    const event = parseBlock(this.buffer)
    this.buffer = ''
    return event ? [event] : []
  }
}

function parseBlock(block: string): ServerSentEvent | null {
  let event = 'message'
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (!line || line.startsWith(':')) continue
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') event = value
    else if (field === 'data') data.push(value)
  }
  if (data.length === 0) return null
  return { event, data: data.join('\n') }
}
