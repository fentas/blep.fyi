// Scroll-spy for the docs pages: highlight the active section in the left nav
// and the right "on this page" TOC. Links opt in with the data-spy attribute.
export function initScrollSpy() {
  const spy = [...document.querySelectorAll('[data-spy]')]
  if (!spy.length) return
  const setActive = (id) => spy.forEach((a) => {
    const on = a.getAttribute('href') === '#' + id
    a.classList.toggle('text-blue', on)
    a.classList.toggle('font-semibold', on)
    if (a.classList.contains('border-l')) a.classList.toggle('border-blue', on)
  })
  const obs = new IntersectionObserver((entries) => {
    const vis = entries.filter((e) => e.isIntersecting).sort((a, b) => a.target.offsetTop - b.target.offsetTop)
    if (vis[0]) setActive(vis[0].target.id)
  }, { rootMargin: '0px 0px -72% 0px' })
  document.querySelectorAll('main section[id]').forEach((s) => obs.observe(s))
}
