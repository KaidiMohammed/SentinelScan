// Proxy /api vers l'API Spring (même origine pour le navigateur : pas de CORS).
// compress désactivé pour ne pas mettre en tampon le flux SSE.
const apiUrl = process.env.API_URL || 'http://localhost:8080'

export default {
  compress: false,
  async rewrites() {
    return [{ source: '/api/:path*', destination: `${apiUrl}/api/:path*` }]
  },
}
