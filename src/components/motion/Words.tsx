/**
 * Splits a heading into words for the staggered word reveal. Spaces stay real
 * text nodes, so the heading reads and copies exactly as written; line breaks
 * in the source ("\n") become <br>.
 */
export function Words({ text }: { text: string }) {
  let index = 0
  return (
    <>
      {text.split('\n').map((line, lineIndex) => (
        <span key={lineIndex}>
          {lineIndex > 0 && <br />}
          {line.split(/(\s+)/).map((part, partIndex) =>
            /^\s+$/.test(part) || !part ? (
              part
            ) : (
              <span key={partIndex} className="word" style={{ '--w': index++ } as React.CSSProperties}>
                <span className="word-inner">{part}</span>
              </span>
            ),
          )}
        </span>
      ))}
    </>
  )
}
