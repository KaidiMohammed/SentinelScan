export default function Home() {
  return (
    <main style={{ maxWidth: 640, margin: '15vh auto', padding: '0 16px', fontFamily: 'system-ui' }}>
      <h1>Votre app est-elle exposée ?</h1>
      <p>Collez l'adresse de votre app pour savoir, en quelques minutes, si elle a des problèmes de sécurité sérieux.</p>
      <form>
        <label htmlFor="url">Adresse de l'app</label>
        <input id="url" type="url" placeholder="https://mon-app.com" disabled style={{ display: 'block', width: '100%' }} />
        <button type="submit" disabled>Scanner mon app</button>
      </form>
      <p role="status">Pas encore implémenté : aucune vérification ne tourne pour l'instant.</p>
    </main>
  )
}
