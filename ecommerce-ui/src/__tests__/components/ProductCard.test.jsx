import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import ProductCard from '../../components/ProductCard'
import { CartProvider } from '../../context/CartContext'
import * as cartHook from '../../context/CartContext'

const renderCard = (overrides = {}) => {
  const product = {
    id: 1, name: 'Running Shoes', sku: 'RS-001',
    price: '89.99', stock: 150, category: 'FOOTWEAR',
    ...overrides,
  }
  render(
    <CartProvider>
      <MemoryRouter><ProductCard product={product} /></MemoryRouter>
    </CartProvider>
  )
  return { product }
}

describe('ProductCard', () => {

  describe('rendering', () => {
    it('displays the product name', () => {
      renderCard()
      expect(screen.getByText('Running Shoes')).toBeInTheDocument()
    })

    it('displays the SKU', () => {
      renderCard()
      expect(screen.getByText('RS-001')).toBeInTheDocument()
    })

    it('displays the formatted price', () => {
      renderCard()
      expect(screen.getByText('$89.99')).toBeInTheDocument()
    })

    it('shows stock count when in stock', () => {
      renderCard({ stock: 150 })
      expect(screen.getByText('150 in stock')).toBeInTheDocument()
    })

    it('shows "out of stock" badge when stock is 0', () => {
      renderCard({ stock: 0 })
      expect(screen.getByText('out of stock')).toBeInTheDocument()
    })

    it('renders category badge in lowercase', () => {
      renderCard({ category: 'ELECTRONICS' })
      expect(screen.getByText('electronics')).toBeInTheDocument()
    })

    it('renders Edit link pointing to the edit page', () => {
      renderCard()
      expect(screen.getByRole('link', { name: /edit/i }))
        .toHaveAttribute('href', '/products/1/edit')
    })
  })

  describe('Add to cart button', () => {
    it('is enabled when product is in stock', () => {
      renderCard({ stock: 5 })
      expect(screen.getByRole('button', { name: /add to cart/i })).not.toBeDisabled()
    })

    it('is disabled when stock is 0', () => {
      renderCard({ stock: 0 })
      expect(screen.getByRole('button', { name: /add to cart/i })).toBeDisabled()
    })

    it('shows "Added ✓" feedback after clicking', async () => {
      renderCard({ stock: 10 })
      await userEvent.click(screen.getByRole('button', { name: /add to cart/i }))
      await waitFor(() => {
        expect(screen.getByText('Added ✓')).toBeInTheDocument()
      })
    })

    it('reverts to "Add to cart" label after the feedback timeout', async () => {
      vi.useFakeTimers()
      renderCard({ stock: 10 })
      await userEvent.setup({ delay: null }).click(
        screen.getByRole('button', { name: /add to cart/i })
      )
      expect(screen.getByText('Added ✓')).toBeInTheDocument()

      vi.advanceTimersByTime(1300)
      await waitFor(() => {
        expect(screen.getByText('Add to cart')).toBeInTheDocument()
      })
      vi.useRealTimers()
    })
  })

  describe('price formatting', () => {
    it('formats integer price with two decimal places', () => {
      renderCard({ price: '100' })
      expect(screen.getByText('$100.00')).toBeInTheDocument()
    })

    it('pads a single decimal to two places', () => {
      renderCard({ price: '9.9' })
      expect(screen.getByText('$9.90')).toBeInTheDocument()
    })

    it('renders a free product as $0.00', () => {
      renderCard({ price: '0' })
      expect(screen.getByText('$0.00')).toBeInTheDocument()
    })
  })
})
