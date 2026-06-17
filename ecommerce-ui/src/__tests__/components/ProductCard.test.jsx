import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import ProductCard from '../../components/ProductCard'

const renderCard = (overrides = {}) => {
  const product = {
    id: 1, name: 'Running Shoes', sku: 'RS-001',
    price: '89.99', stock: 150, category: 'FOOTWEAR',
    ...overrides,
  }
  const onBuy = vi.fn()
  render(<MemoryRouter><ProductCard product={product} onBuy={onBuy} /></MemoryRouter>)
  return { product, onBuy }
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

  describe('Buy button', () => {
    it('is enabled when product is in stock', () => {
      renderCard({ stock: 5 })
      expect(screen.getByRole('button', { name: /buy/i })).not.toBeDisabled()
    })

    it('is disabled when stock is 0', () => {
      renderCard({ stock: 0 })
      expect(screen.getByRole('button', { name: /buy/i })).toBeDisabled()
    })

    it('calls onBuy with the product object when clicked', async () => {
      const { onBuy, product } = renderCard({ stock: 10 })
      await userEvent.click(screen.getByRole('button', { name: /buy/i }))
      expect(onBuy).toHaveBeenCalledOnce()
      expect(onBuy).toHaveBeenCalledWith(product)
    })

    it('does NOT call onBuy when out of stock', async () => {
      const { onBuy } = renderCard({ stock: 0 })
      await userEvent.click(screen.getByRole('button', { name: /buy/i }))
      expect(onBuy).not.toHaveBeenCalled()
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
