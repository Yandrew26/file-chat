import { describe, expect, it } from 'vitest'
import { citedNumbers, linkCitations } from './citations'

describe('linkCitations', () => {
  it('turns numeric citations into citation links', () => {
    expect(linkCitations('Revenue grew [1][2].')).toBe('Revenue grew [1](#cite-1)[2](#cite-2).')
  })

  it('leaves existing markdown links alone', () => {
    expect(linkCitations('See [1](https://example.com)')).toBe('See [1](https://example.com)')
  })

  it('converts legacy [Doc: name] citations', () => {
    expect(linkCitations('Fact [Doc: report.pdf]')).toBe('Fact [report.pdf](#doc-report.pdf)')
  })

  it('does not touch code', () => {
    const markdown = 'Use `arr[1]` here [2]\n\n```\nx = y[3]\n```'
    expect(linkCitations(markdown)).toBe('Use `arr[1]` here [2](#cite-2)\n\n```\nx = y[3]\n```')
  })
})

describe('citedNumbers', () => {
  it('lists citations in order of first use, outside code', () => {
    expect(citedNumbers('A [2] B [1] C [2] `[9]`')).toEqual([2, 1])
  })
})
