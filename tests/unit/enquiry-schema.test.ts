import { describe, expect, it } from 'vitest'
import { intendedStartOptions, validate } from '@/lib/enquiry/schema'

const valid = {
  formType: 'visit',
  name: 'Aisha Rahman',
  email: 'aisha@example.com',
  phone: '+60 12 345 6789',
  stage: 'primary',
  intendedStart: '',
  message: '',
  consentPrivacy: 'on',
}

describe('enquiry validation', () => {
  it('accepts a complete visit request', () => {
    expect(validate(valid).errors).toBeNull()
  })

  it('requires name, a valid email and privacy consent', () => {
    const { errors } = validate({ ...valid, name: ' ', email: 'not-an-email', consentPrivacy: undefined })
    expect(errors).toMatchObject({
      name: expect.any(String),
      email: expect.stringContaining('valid email'),
      consentPrivacy: expect.any(String),
    })
  })

  it('requires a stage for admissions forms but not for general contact', () => {
    expect(validate({ ...valid, stage: '' }).errors?.stage).toBeDefined()
    expect(validate({ ...valid, formType: 'contact', stage: '', message: 'Where do I park for a visit?' }).errors).toBeNull()
  })

  it('requires a meaningful message for general contact', () => {
    expect(validate({ ...valid, formType: 'contact', message: 'hi' }).errors?.message).toBeDefined()
  })

  it('rejects malformed phone numbers and over-long messages', () => {
    expect(validate({ ...valid, phone: 'call me maybe' }).errors?.phone).toBeDefined()
    expect(validate({ ...valid, message: 'x'.repeat(2001) }).errors?.message).toBeDefined()
  })

  it('rejects unknown form types and stages', () => {
    expect(validate({ ...valid, formType: 'newsletter' }).errors).not.toBeNull()
    expect(validate({ ...valid, stage: 'university' }).errors?.stage).toBeDefined()
  })
})

describe('intended start options', () => {
  it('rolls over to the next academic year in August', () => {
    expect(intendedStartOptions(new Date('2026-10-22'))[1]).toBe('Academic year 2027/28')
    expect(intendedStartOptions(new Date('2026-03-01'))[1]).toBe('Academic year 2026/27')
  })
})
