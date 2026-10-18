import { expect, test } from 'claude-code/testing'

import { gatewayLinks, toastFor } from './register'

const LINK = 'http://127.0.0.1:18321/open?module=ОбщийМодуль.Пример&line=15'
const WITH_LINK = `See [Пример:15](src/x.bsl:15) ([EDT](${LINK})).`
const WITHOUT_LINK = 'See [docs](https://example.com).'

test('collects gateway links only, once each', async () => {
  expect(gatewayLinks(`${WITH_LINK} ${WITH_LINK} ${WITHOUT_LINK}`)).toEqual([LINK])
})

test('tells opened, not found and no answer apart', async () => {
  expect(toastFor(0, '{"status":"ok","code":200,"message":"Navigation dispatched"}')).toBe('Opened in EDT')
  expect(toastFor(0, '{"status":"error","code":404,"message":"Object not found"}')).toBe('Not found in EDT')
  expect(toastFor(0, '{"status":"error","code":400,"message":"Missing required parameters"}')).toBe('EDT could not open it')
  expect(toastFor(7, '')).toBe('EDT did not respond: is EDT running?')
  expect(toastFor(0, '<html>')).toBe('EDT did not respond: is EDT running?')
})

test('draws a block with a gateway link, leaves other blocks alone', async ($, on) => {
  // Stands for the engine's own drawing of a reply block.
  on('ui.render', { component: 'AssistantMessage' }, ($, e) => {
    const { Text } = $.ui.resolve(e)
    return <Text>{e.props.text}</Text>
  })

  for (const surface of ['terminal', 'desktop'] as const) {
    const withLink = await $.ui.mount({
      plugin: 'edt-links',
      surface,
      component: 'AssistantMessage',
      props: { text: WITH_LINK, isFirstOfReply: true },
    })
    expect(await withLink.find({ key: 'edt-links' })).toBeDefined()
    await withLink.unmount()

    const withoutLink = await $.ui.mount({
      plugin: 'edt-links',
      surface,
      component: 'AssistantMessage',
      props: { text: WITHOUT_LINK, isFirstOfReply: true },
    })
    expect(await withoutLink.find({ key: 'edt-links' })).toBeUndefined()
    expect(await withoutLink.find({ type: 'Text', text: /docs/ })).toBeDefined()
    await withoutLink.unmount()
  }
})
