import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ordersApi } from '../api/client'
import { useCart } from '../context/CartContext'

function validateExpiry(value) {
  // Expected format MM/YYYY, e.g. 04/2027
  const match = /^(\d{2})\/(\d{4})$/.exec(value.trim())
  if (!match) return 'Use MM/YYYY format'

  const month = Number(match[1])
  const year = Number(match[2])
  if (month < 1 || month > 12) return 'Month must be between 01 and 12'

  const now = new Date()
  const currentMonth = now.getMonth() + 1
  const currentYear = now.getFullYear()
  if (year < currentYear || (year === currentYear && month < currentMonth)) {
    return 'Card has expired'
  }
  if (year > currentYear + 20) return 'Expiry year looks invalid'

  return null
}

function validateCvv(value) {
  if (!/^\d{3}$/.test(value.trim())) return 'CVV must be exactly 3 digits'
  return null
}

function formatExpiryInput(raw) {
  const digits = raw.replace(/\D/g, '').slice(0, 6) // MM + YYYY = 6 digits
  if (digits.length <= 2) return digits
  return `${digits.slice(0, 2)}/${digits.slice(2)}`
}

export default function CheckoutModal({ onClose }) {
  const { items, total, clearCart } = useCart()
  const [card, setCard] = useState('')
  const [expiry, setExpiry] = useState('')
  const [cvv, setCvv] = useState('')
  const [holder, setHolder] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const navigate = useNavigate()

  const formatCard = v => v.replace(/\D/g, '').slice(0, 16).replace(/(.{4})/g, '$1 ').trim()

  function runValidation() {
    const errors = {}

    const cardDigits = card.replace(/\s/g, '')
    if (cardDigits.length !== 16) {
      errors.card = 'Card number must be exactly 16 digits'
    }

    const expiryError = validateExpiry(expiry)
    if (expiryError) errors.expiry = expiryError

    const cvvError = validateCvv(cvv)
    if (cvvError) errors.cvv = cvvError

    if (!holder.trim()) errors.holder = 'Name on card is required'

    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    if (!runValidation()) return

    setLoading(true)
    try {
      const order = await ordersApi.place({
        items: items.map(i => ({ productId: i.product.id, quantity: i.quantity })),
        cardNumber: card.replace(/\s/g, ''),
      })
      clearCart()
      onClose()
      navigate(`/orders/${order.id}`)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
      onClick={e => e.target === e.currentTarget && onClose()}
    >
      <div className="card w-full max-w-sm p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-5">
          <h2 className="font-semibold text-gray-900">Checkout</h2>
          <button onClick={onClose} className="btn-ghost p-1 rounded-full" aria-label="Close">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>

        <div className="bg-surface rounded-lg p-3 mb-5 text-sm space-y-1">
          {items.map(i => (
            <div key={i.product.id} className="flex justify-between text-gray-600">
              <span>{i.product.name} × {i.quantity}</span>
              <span>${(Number(i.product.price) * i.quantity).toFixed(2)}</span>
            </div>
          ))}
          <div className="flex justify-between font-semibold text-gray-900 border-t border-gray-100 pt-2 mt-2">
            <span>Total</span>
            <span>${total.toFixed(2)}</span>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-3" noValidate>
          <div>
            <label className="label">Card number</label>
            <input
              value={card}
              onChange={e => setCard(formatCard(e.target.value))}
              placeholder="1234 5678 9012 3456"
              className={`input font-mono ${fieldErrors.card ? 'border-red-300' : ''}`}
            />
            {fieldErrors.card && <p className="text-xs text-red-500 mt-1">{fieldErrors.card}</p>}
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">Expiry</label>
              <input
                value={expiry}
                onChange={e => setExpiry(formatExpiryInput(e.target.value))}
                placeholder="MM/YYYY"
                inputMode="numeric"
                className={`input ${fieldErrors.expiry ? 'border-red-300' : ''}`}
              />
              {fieldErrors.expiry && <p className="text-xs text-red-500 mt-1">{fieldErrors.expiry}</p>}
            </div>
            <div>
              <label className="label">CVV</label>
              <input
                value={cvv}
                onChange={e => setCvv(e.target.value.replace(/\D/g, '').slice(0, 3))}
                placeholder="123"
                inputMode="numeric"
                className={`input ${fieldErrors.cvv ? 'border-red-300' : ''}`}
              />
              {fieldErrors.cvv && <p className="text-xs text-red-500 mt-1">{fieldErrors.cvv}</p>}
            </div>
          </div>
          <div>
            <label className="label">Name on card</label>
            <input
              value={holder}
              onChange={e => setHolder(e.target.value)}
              placeholder="Jane Smith"
              className={`input ${fieldErrors.holder ? 'border-red-300' : ''}`}
            />
            {fieldErrors.holder && <p className="text-xs text-red-500 mt-1">{fieldErrors.holder}</p>}
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
            disabled={loading || items.length === 0}
            className="btn-primary w-full justify-center"
          >
            {loading ? 'Processing…' : `Pay $${total.toFixed(2)}`}
          </button>
        </form>
      </div>
    </div>
  )
}
