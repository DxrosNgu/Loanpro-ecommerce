import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import OrderConfirmPage from '../../pages/OrderConfirmPage'
import * as client from '../../api/client'

vi.mock('../../api/client', () => ({
  ordersApi: { get: vi.fn() }
}))

const qc = () => new QueryClient({ defaultOptions: { queries: { retry: false } } })

const renderPage = (orderId = '42') =>
  render(
    <QueryClientProvider client={qc()}>
      <MemoryRouter initialEntries={[`/orders/${orderId}`]}>
        <Routes>
          <Route path="/orders/:id" element={<OrderConfirmPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )

const paidOrder = {
  id: 42, status: 'PAID',
  totalAmount: 179.98, paymentRef: 'TXN-ABCD1234',
  items: [
    { productName: 'Running Shoes', productSku: 'RS-001', quantity: 2, unitPrice: 89.99, subtotal: 179.98 }
  ]
}

const failedOrder = {
  id: 43, status: 'FAILED',
  totalAmount: 179.98, paymentRef: null,
  items: [
    { productName: 'Running Shoes', productSku: 'RS-001', quantity: 2, unitPrice: 89.99, subtotal: 179.98 }
  ]
}

describe('OrderConfirmPage', () => {

  beforeEach(() => vi.clearAllMocks())

  describe('PAID order', () => {
    it('shows "Payment confirmed" heading', async () => {
      client.ordersApi.get.mockResolvedValue(paidOrder)
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Payment confirmed')).toBeInTheDocument()
      })
    })

    it('shows the transaction reference', async () => {
      client.ordersApi.get.mockResolvedValue(paidOrder)
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('TXN-ABCD1234')).toBeInTheDocument()
      })
    })

    it('shows order ID', async () => {
      client.ordersApi.get.mockResolvedValue(paidOrder)
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('#42')).toBeInTheDocument()
      })
    })

    it('shows PAID status badge', async () => {
      client.ordersApi.get.mockResolvedValue(paidOrder)
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('PAID')).toBeInTheDocument()
      })
    })

    it('lists the ordered items', async () => {
      client.ordersApi.get.mockResolvedValue(paidOrder)
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('Running Shoes')).toBeInTheDocument()
        expect(screen.getByText('RS-001 × 2')).toBeInTheDocument()
      })
    })

    it('shows the total amount', async () => {
      client.ordersApi.get.mockResolvedValue(paidOrder)
      renderPage()
      await waitFor(() => {
        expect(screen.getByText('$179.98')).toBeInTheDocument()
      })
    })

    it('shows "Continue shopping" link', async () => {
      client.ordersApi.get.mockResolvedValue(paidOrder)
      renderPage()
      await waitFor(() => {
        expect(screen.getByRole('link', { name: /continue shopping/i })).toBeInTheDocument()
      })
    })

    it('does NOT show "Try again" button for successful orders', async () => {
      client.ordersApi.get.mockResolvedValue(paidOrder)
      renderPage()
      await waitFor(() => {
        expect(screen.queryByRole('button', { name: /try again/i })).not.toBeInTheDocument()
      })
    })
  })

  describe('FAILED order', () => {
    it('shows "Payment failed" heading', async () => {
      client.ordersApi.get.mockResolvedValue(failedOrder)
      renderPage('43')
      await waitFor(() => {
        expect(screen.getByText('Payment failed')).toBeInTheDocument()
      })
    })

    it('shows FAILED status badge', async () => {
      client.ordersApi.get.mockResolvedValue(failedOrder)
      renderPage('43')
      await waitFor(() => {
        expect(screen.getByText('FAILED')).toBeInTheDocument()
      })
    })

    it('shows "Try again" button for failed orders', async () => {
      client.ordersApi.get.mockResolvedValue(failedOrder)
      renderPage('43')
      await waitFor(() => {
        expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
      })
    })

    it('shows no-charge notice', async () => {
      client.ordersApi.get.mockResolvedValue(failedOrder)
      renderPage('43')
      await waitFor(() => {
        expect(screen.getByText(/no charge was made/i)).toBeInTheDocument()
      })
    })
  })

  describe('loading and error states', () => {
    it('shows error UI when API call fails', async () => {
      client.ordersApi.get.mockRejectedValue(new Error('Not found'))
      renderPage('999')
      await waitFor(() => {
        expect(screen.getByText(/could not load order/i)).toBeInTheDocument()
      })
    })
  })
})
