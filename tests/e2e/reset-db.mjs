// Recreates the end-to-end test database so every run starts clean.
import pg from 'pg'

const url = new URL(process.env.E2E_DATABASE_URL || 'postgres://lt:lt@localhost:5432/labeltrunc_e2e')
const dbName = url.pathname.slice(1)
if (!/e2e|test/.test(dbName)) throw new Error(`Refusing to reset "${dbName}": name must contain "e2e" or "test".`)
const admin = new URL(url)
admin.pathname = '/postgres'
const client = new pg.Client({ connectionString: admin.toString() })
await client.connect()
await client.query(`SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = $1 AND pid <> pg_backend_pid()`, [dbName])
await client.query(`DROP DATABASE IF EXISTS "${dbName}"`)
await client.query(`CREATE DATABASE "${dbName}" OWNER "${url.username}"`)
await client.end()
const fs = await import('fs')
fs.rmSync(new URL('../../media-e2e', import.meta.url), { recursive: true, force: true })
console.log(`Reset ${dbName}`)
