import Link from 'next/link'
import './editor-guide.scss'

const tasks = [
  { href: '/admin/collections/pages', title: 'Edit a page', text: 'Open a page, change its sections, then use Live Preview to check it before publishing.' },
  { href: '/admin/collections/stories/create', title: 'Post news', text: 'Write a story with one approved photograph. It appears in “Latest stories” once published.' },
  { href: '/admin/collections/events/create', title: 'Add an event', text: 'Open days and information sessions. They disappear from the website automatically after they end.' },
  { href: '/admin/collections/enquiries', title: 'Read enquiries', text: 'Messages sent from the website forms. Update the status as you respond.' },
  { href: '/admin/globals/admissions', title: 'Fees & applying', text: 'Academic year, fee schedule link, waiver and application link in one place.' },
  { href: '/admin/globals/header', title: 'Menus', text: 'Change the main menu and footer links. Drag items to reorder.' },
]

export const EditorGuide = () => (
  <section className="isp-guide" aria-labelledby="isp-guide-title">
    <h2 id="isp-guide-title">Website editor</h2>
    <p>
      Changes are saved as drafts automatically. Nothing reaches the public website until you press <strong>Publish</strong>.
      Contributors can prepare drafts; an editor publishes them.
    </p>
    <ul>
      {tasks.map((task) => (
        <li key={task.href}>
          <Link href={task.href}>
            <span className="isp-guide__title">{task.title}</span>
            <span className="isp-guide__text">{task.text}</span>
          </Link>
        </li>
      ))}
    </ul>
    <p className="isp-guide__note">
      Before publishing facts, figures, fees or photographs of students, check they have been approved by the responsible owner.
      The full guide is in <code>docs/EDITOR-GUIDE.md</code>.
    </p>
  </section>
)
