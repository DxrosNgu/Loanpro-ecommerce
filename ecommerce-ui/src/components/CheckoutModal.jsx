import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ordersApi } from '../api/client'

export default function CheckoutModal({ product, quantity = 1, onClose }) {
  const [card, setCard] = useState('')
  const [expiry, setExpiry] = useState('')
  const [cvv, setCvv] = useState('')
  const [holder, setHolder] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const navigate = useNavigate()

  const total = (Number(product.price) * quantity).toFixed(2)

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const order = await ordersApi.place({
        items: [{ productId: product.id, quantity }],
        cardNumber: card.replace(/\s/g, ''),
      })
      onClose()
      navigate(`/orders/${order.id}`)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  const formatCard = v => v.replace(/\D/g,'').slice(0,16).replace(/(.{4})/g,'$1 ').trim()

  return (
    <div
      className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
      onClick={e => e.target === e.currentTarget && onClose()}
    >
      <div className="card w-full max-w-sm p-6">
        <div className="flex items-center justify-between mb-5">
          <h2 className="font-semibold text-gray-900">Checkout</h2>
          <button onClick={onClose} className="btn-ghost p-1 rounded-full" aria-label="Close">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>

        <div className="bg-surface rounded-lg p-3 mb-5 text-sm">
          <div className="flex justify-between text-gray-600 mb-1">
            <span>{product.name} × {quantity}</span>
            <span>${total}</span>
          </div>
          <div className="flex justify-between font-semibold text-gray-900 border-t border-gray-100 pt-2 mt-2">
            <span>Total</span>
            <span>${total}</span>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="label">Card number</label>
            <input
              required
              value={card}
              onChange={e => setCard(formatCard(e.target.value))}
              placeholder="1234 5678 9012 3456"
              className="input font-mono"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">Expiry</label>
              <input
                required
                value={expiry}
                onChange={e => setExpiry(e.target.value)}
                placeholder="MM / YY"
                className="input"
              />
            </div>
            <div>
              <label className="label">CVV</label>
              <input
                required
                value={cvv}
                onChange={e => setCvv(e.target.value.slice(0,4))}
                placeholder="123"
                className="input"
              />
            </div>
          </div>
          <div>
            <label className="label">Name on card</label>
            <input
              required
              value={holder}
              onChange={e => setHolder(e.target.value)}
              placeholder="Jane Smith"
              className="input"
            />
          </div>

          <p className="text-[11px] text-gray-400">
            Tip: cards ending in 0000 will be declined
          </p>

          {error && (
            <div className="bg-red-50 border border-red-100 text-red-700 text-sm rounded-lg p-3">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="btn-primary w-full justify-center"
          >
            {loading ? 'Processing…' : `Pay $${total}`}
          </button>
        </form>
      </div>
    </div>
  )
}
