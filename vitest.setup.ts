import 'dotenv/config'

// Integration tests run against a separate database, never the development one.
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL || 'postgres://lt:lt@localhost:5432/labeltrunc_test'
process.env.PAYLOAD_DB_PUSH = 'true'
process.env.PAYLOAD_SECRET = process.env.PAYLOAD_SECRET || 'test-secret-0123456789'
process.env.SERVER_URL = 'http://localhost:3000'
