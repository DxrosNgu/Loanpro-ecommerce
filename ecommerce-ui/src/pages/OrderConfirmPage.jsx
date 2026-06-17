import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ordersApi } from '../api/client'

export default function OrderConfirmPage() {
  const { id } = useParams()

  const { data: order, isLoading, isError } = useQuery({
    queryKey: ['order', id],
    queryFn: () => ordersApi.get(id),
  })

  if (isLoading) return <div className="animate-pulse h-64 bg-gray-50 rounded-xl" />
  if (isError) return (
    <div className="text-center py-16">
      <p className="text-red-500 mb-4">Could not load order #{id}</p>
      <Link to="/" className="btn-secondary">Back to products</Link>
    </div>
  )

  const isPaid   = order.status === 'PAID'
  const isFailed = order.status === 'FAILED'

  return (
    <div className="max-w-lg mx-auto">
      {/* Status banner */}
      <div className={`rounded-2xl p-8 text-center mb-6 ${
        isPaid   ? 'bg-emerald-50 border border-emerald-100' :
        isFailed ? 'bg-red-50 border border-red-100' :
                   'bg-amber-50 border border-amber-100'
      }`}>
        {isPaid && (
          <>
            <div className="w-14 h-14 bg-emerald-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#059669" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
            </div>
            <h1 className="text-xl font-semibold text-emerald-900 mb-1">Payment confirmed</h1>
            <p className="text-sm text-emerald-700">Your order is being processed</p>
          </>
        )}
        {isFailed && (
          <>
            <div className="w-14 h-14 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#dc2626" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </div>
            <h1 className="text-xl font-semibold text-red-900 mb-1">Payment failed</h1>
            <p className="text-sm text-red-700">No charge was made. Please try a different card.</p>
          </>
        )}
      </div>

      {/* Order details */}
      <div className="card p-5 space-y-4">
        <div className="flex items-center justify-between text-sm">
          <span className="text-gray-500">Order</span>
          <span className="font-mono font-medium text-gray-900">#{order.id}</span>
        </div>
        {order.paymentRef && (
          <div className="flex items-center justify-between text-sm">
            <span className="text-gray-500">Transaction</span>
            <span className="font-mono text-xs text-gray-700">{order.paymentRef}</span>
          </div>
        )}
        <div className="flex items-center justify-between text-sm">
          <span className="text-gray-500">Status</span>
          <span className={`badge ${isPaid ? 'badge-green' : isFailed ? 'badge-red' : 'badge-amber'}`}>
            {order.status}
          </span>
        </div>

        {/* Line items */}
        <div className="border-t border-gray-100 pt-4 space-y-2">
          {order.items.map((item, i) => (
            <div key={i} className="flex items-center justify-between text-sm">
              <div>
                <p className="font-medium text-gray-900">{item.productName}</p>
                <p className="text-xs font-mono text-gray-400">{item.productSku} × {item.quantity}</p>
              </div>
              <span className="font-medium">${Number(item.subtotal).toFixed(2)}</span>
            </div>
          ))}
        </div>

        <div className="border-t border-gray-100 pt-3 flex items-center justify-between">
          <span className="font-semibold text-gray-900">Total</span>
          <span className="font-bold text-lg">${Number(order.totalAmount).toFixed(2)}</span>
        </div>
      </div>

      <div className="flex gap-3 mt-6">
        <Link to="/" className="btn-primary flex-1 justify-center">
          Continue shopping
        </Link>
        {isFailed && (
          <button onClick={() => window.history.back()} className="btn-secondary">
            Try again
          </button>
        )}
      </div>
    </div>
  )
}
