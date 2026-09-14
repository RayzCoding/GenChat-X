import type { MouseEvent } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { Components } from 'react-markdown'

interface MarkdownContentProps {
  content: string
  isStreaming?: boolean
  className?: string
  onLinkClick?: (href: string, event: MouseEvent<HTMLAnchorElement>) => void
}

const markdownComponents: Components = {
  h1: ({ children }) => <h1>{children}</h1>,
  h2: ({ children }) => <h2>{children}</h2>,
  h3: ({ children }) => <h3>{children}</h3>,
  h4: ({ children }) => <h4>{children}</h4>,
  ul: ({ children }) => <ul>{children}</ul>,
  ol: ({ children }) => <ol>{children}</ol>,
  li: ({ children }) => <li>{children}</li>,
  p: ({ children }) => <p>{children}</p>,
  blockquote: ({ children }) => <blockquote>{children}</blockquote>,
  hr: () => <hr />,
  table: ({ children }) => (
    <div className="markdown-table-wrap">
      <table>{children}</table>
    </div>
  ),
  a: ({ href, children }) => (
    <a href={href} target="_blank" rel="noopener noreferrer">
      {children}
    </a>
  ),
}

export function MarkdownContent({ content, isStreaming, className, onLinkClick }: MarkdownContentProps) {
  if (!content && !isStreaming) return null

  const components: Components = onLinkClick
    ? {
        ...markdownComponents,
        a: ({ href, children }) => (
          <a
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            onClick={(event) => {
              if (href) {
                onLinkClick(href, event)
              }
            }}
          >
            {children}
          </a>
        ),
      }
    : markdownComponents

  return (
    <div className={['markdown-body', className].filter(Boolean).join(' ')}>
      {content ? (
        <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
          {content}
        </ReactMarkdown>
      ) : isStreaming ? (
        <span className="inline-block h-4 w-4 animate-pulse rounded-full bg-primary/40" />
      ) : null}
    </div>
  )
}
