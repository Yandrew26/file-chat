/**
 * Answers cite retrieved passages as [1], [2][3]… Older answers may use [Doc: name].
 * These helpers turn citations into links the markdown renderer can style, while leaving
 * code blocks and inline code untouched.
 */

const CITATION = /\[(\d{1,2})\](?!\()/g
const DOC_CITATION = /\[Doc:\s*([^\]]+)\]/g

export const CITATION_PREFIX = '#cite-'
export const DOC_PREFIX = '#doc-'

function mapOutsideCode(markdown: string, transform: (text: string) => string): string {
  // Split into fenced blocks and inline code spans; odd indexes are code
  const parts = markdown.split(/(```[\s\S]*?(?:```|$)|`[^`\n]*`)/g)
  return parts.map((part, index) => (index % 2 === 1 ? part : transform(part))).join('')
}

export function linkCitations(markdown: string): string {
  return mapOutsideCode(markdown, (text) =>
    text
      .replace(DOC_CITATION, (_, name: string) => `[${name.trim()}](${DOC_PREFIX}${encodeURIComponent(name.trim())})`)
      .replace(CITATION, (_, n: string) => `[${n}](${CITATION_PREFIX}${n})`),
  )
}

/** Citation numbers used in an answer, in order of first appearance. */
export function citedNumbers(markdown: string): number[] {
  const seen = new Set<number>()
  mapOutsideCode(markdown, (text) => {
    for (const match of text.matchAll(CITATION)) seen.add(Number(match[1]))
    return text
  })
  return [...seen]
}
