import type { Register } from 'claude-code'

// Markdown links whose target is the EDT URL Navigator gateway.
const GATEWAY_LINK = /\]\((http:\/\/127\.0\.0\.1:18321\/open\?[^)\s]+)\)/g

export const gatewayLinks = (text: string): string[] =>
  [...new Set([...text.matchAll(GATEWAY_LINK)].flatMap(m => m[1] ?? []))]

// The toast for the gateway's answer: 200 opened, 404 not found (plugin 1.1.0+).
export const toastFor = (exitCode: number, stdout: string): string => {
  if (exitCode !== 0) {
    return 'EDT did not respond: is EDT running?'
  }

  try {
    const answer: { status?: string; code?: number } = JSON.parse(stdout)

    if (answer.status === 'ok') {
      return 'Opened in EDT'
    }

    return answer.code === 404 ? 'Not found in EDT' : 'EDT could not open it'
  } catch {
    return 'EDT did not respond: is EDT running?'
  }
}

export const register: Register = on => {
  on('ui.render', { component: 'AssistantMessage' }, ($, e, next) => {
    const links = gatewayLinks(e.props.text)

    if (links.length === 0) {
      return next(e)
    }

    const { Markdown } = $.ui.resolve(e)

    return (
      <Markdown
        key="edt-links"
        text={e.props.text}
        pressableLinks={links}
        onLinkPress={async link => {
          // encodeURI keeps the argv ASCII; the gateway decodes the query.
          const { exitCode, stdout } = await $.process.run(
            ['curl', '-s', '-m', '5', encodeURI(link.href)],
            { timeoutMs: 10_000 },
          )

          $.ui.toast(toastFor(exitCode, stdout))
        }}
      />
    )
  })
}
