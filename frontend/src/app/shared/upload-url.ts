/**
 * Presigned LocalStack URLs may use Docker DNS or virtual-hosted style.
 * Host-side fetch needs path-style localhost:4566.
 */
export function browserUploadUrl(uploadUrl: string): string {
  try {
    const u = new URL(uploadUrl);
    const host = u.hostname;
    if (host.endsWith('.localstack') || (host !== 'localhost' && host.endsWith('.localhost'))) {
      const bucket = host.split('.')[0];
      const port = u.port || '4566';
      return `${u.protocol}//localhost:${port}/${bucket}${u.pathname}${u.search}`;
    }
    u.hostname = host.replace(/^localstack$/, 'localhost');
    return u.toString();
  } catch {
    return uploadUrl
      .replace('://localstack:', '://localhost:')
      .replace('.localstack:', '.localhost:');
  }
}
