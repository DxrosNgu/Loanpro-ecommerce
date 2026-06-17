import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useCart } from '../context/CartContext'
import CheckoutModal from '../components/CheckoutModal'

export default function CartPage() {
  const { items, removeItem, updateQuantity, total } = useCart()
  const [checkingOut, setCheckingOut] = useState(false)

  if (items.length === 0) {
    return (
      <div className="text-center py-20">
        <p className="text-lg font-medium text-gray-700 mb-1">Your cart is empty</p>
        <p className="text-sm text-gray-400 mb-6">Add some products to get started</p>
        <Link to="/" className="btn-primary">Browse products</Link>
      </div>
    )
  }

  return (
    <>
      <div className="mb-6 flex items-center gap-2 text-sm text-gray-500">
        <Link to="/" className="hover:text-brand-600">Products</Link>
        <span>/</span>
        <span className="text-gray-900 font-medium">Cart</span>
      </div>

      <h1 className="text-xl font-semibold text-gray-900 mb-5">Your cart</h1>

      <div className="space-y-3 mb-6">
        {items.map(({ product, quantity }) => {
          const lineTotal = (Number(product.price) * quantity).toFixed(2)
          return (
            <div key={product.id} className="card p-4 flex items-center gap-4">
              <div className="w-14 h-14 bg-surface rounded-lg flex items-center justify-center text-2xl text-gray-300 font-mono shrink-0">
                {product.name.charAt(0)}
              </div>

              <div className="flex-1 min-w-0">
                <Link
                  to={`/products/${product.id}`}
                  className="font-medium text-gray-900 hover:text-brand-600 line-clamp-1"
                >
                  {product.name}
                </Link>
                <p className="text-xs font-mono text-gray-400">{product.sku}</p>
              </div>

              <div className="flex items-center gap-2 border border-gray-200 rounded-lg px-2 shrink-0">
                <button
                  onClick={() => updateQuantity(product.id, quantity - 1)}
                  disabled={quantity <= 1}
                  className="py-1.5 px-1 text-gray-500 hover:text-gray-900 disabled:opacity-30"
                  aria-label="Decrease quantity"
                >−</button>
                <span className="w-8 text-center font-medium text-sm">{quantity}</span>
                <button
                  onClick={() => updateQuantity(product.id, quantity + 1)}
                  disabled={quantity >= product.stock}
                  className="py-1.5 px-1 text-gray-500 hover:text-gray-900 disabled:opacity-30"
                  aria-label="Increase quantity"
                >+</button>
              </div>

              <span className="font-semibold text-gray-900 w-20 text-right shrink-0">
                ${lineTotal}
              </span>

              <button
                onClick={() => removeItem(product.id)}
                className="btn-ghost p-1.5 rounded-full shrink-0"
                aria-label={`Remove ${product.name}`}
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/>
                  <path d="M10 11v6M14 11v6"/>
                </svg>
              </button>
            </div>
          )
        })}
      </div>

      <div className="card p-5 flex items-center justify-between">
        <div>
          <p className="text-sm text-gray-500">Total</p>
          <p className="text-2xl font-bold text-gray-900">${total.toFixed(2)}</p>
        </div>
        <button onClick={() => setCheckingOut(true)} className="btn-primary">
          Proceed to checkout
        </button>
      </div>

      {checkingOut && <CheckoutModal onClose={() => setCheckingOut(false)} />}
    </>
  )
}
