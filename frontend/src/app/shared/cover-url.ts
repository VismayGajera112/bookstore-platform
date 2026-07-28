import { environment } from '../../environments/environment';

/**
 * Catalog may store LocalStack HTTP URLs or legacy s3:// refs.
 * Browsers need an http(s) URL against localhost:4566 for LocalStack.
 */
export function coverImageUrl(coverUrl: string | null | undefined): string | null {
  if (!coverUrl) {
    return null;
  }
  if (coverUrl.startsWith('http://') || coverUrl.startsWith('https://')) {
    return coverUrl.replace('://localstack:', '://localhost:');
  }
  const base = environment.localstackPublicUrl.replace(/\/$/, '');
  if (coverUrl.startsWith('s3://')) {
    const withoutScheme = coverUrl.substring('s3://'.length);
    const slash = withoutScheme.indexOf('/');
    if (slash > 0) {
      const bucket = withoutScheme.substring(0, slash);
      const key = withoutScheme.substring(slash + 1);
      return `${base}/${bucket}/${key}`;
    }
  }
  return null;
}
