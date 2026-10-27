export const escapeHtml = (value: unknown): string =>
  String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

/** Minimal, client-safe HTML email wrapper in the boutique's colours. */
export const emailLayout = (heading: string, bodyHtml: string): string => `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>${escapeHtml(heading)}</title></head>
<body style="margin:0;background:#F4EDE3;font-family:Arial,Helvetica,sans-serif;color:#2A1B20;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#F4EDE3;padding:24px 12px;">
    <tr><td align="center">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#FBF7F1;border:1px solid #E0D3C3;border-radius:12px;">
        <tr><td style="padding:24px 28px;border-bottom:1px solid #E0D3C3;font-family:Georgia,'Times New Roman',serif;font-size:24px;font-weight:bold;color:#2A1B20;">labeltrunc<span style="color:#501C2B">.</span></td></tr>
        <tr><td style="padding:28px;font-size:16px;line-height:1.6;">
          <h1 style="margin:0 0 16px;font-family:Georgia,'Times New Roman',serif;font-size:22px;color:#501C2B;">${escapeHtml(heading)}</h1>
          ${bodyHtml}
        </td></tr>
        <tr><td style="padding:16px 28px;border-top:1px solid #E0D3C3;font-size:13px;color:#665459;">Label Trunc &middot; 11-2A, Level 2, Ukay Boulevard, 68000 Ampang, Selangor</td></tr>
      </table>
    </td></tr>
  </table>
</body></html>`
