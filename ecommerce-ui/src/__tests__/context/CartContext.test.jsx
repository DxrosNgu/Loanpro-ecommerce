import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CartProvider, useCart } from '../../context/CartContext'

// A tiny harness exposes cart state and actions as plain text/buttons so tests
// can interact with the context the same way a real component tree would.
function CartHarness() {
  const { items, addItem, removeItem, updateQuantity, syncProduct, clearCart, itemCount, total } = useCart()

  return (
    <div>
      <p data-testid="item-count">{itemCount}</p>
      <p data-testid="total">{total.toFixed(2)}</p>
      <ul>
        {items.map(i => (
          <li key={i.product.id} data-testid={`item-${i.product.id}`}>
            {i.product.name} — ${Number(i.product.price).toFixed(2)} × {i.quantity}
          </li>
        ))}
      </ul>

      <button onClick={() => addItem({ id: 1, name: 'Backpack', price: 64.99, stock: 10 }, 1)}>
        add backpack
      </button>
      <button onClick={() => addItem({ id: 2, name: 'Water Bottle', price: 12.50, stock: 50 }, 1)}>
        add bottle
      </button>
      <button onClick={() => removeItem(1)}>remove backpack</button>
      <button onClick={() => updateQuantity(1, 3)}>set backpack qty 3</button>
      <button onClick={() => syncProduct({ id: 1, name: 'Backpack', price: 34.99, stock: 10 })}>
        sync backpack price drop
      </button>
      <button onClick={() => syncProduct({ id: 1, name: 'Backpack', price: 34.99, stock: 2 })}>
        sync backpack with reduced stock
      </button>
      <button onClick={clearCart}>clear cart</button>
    </div>
  )
}

const renderCart = () =>
  render(<CartProvider><CartHarness /></CartProvider>)

describe('CartContext', () => {

  describe('addItem', () => {
    it('adds a new product with the given quantity', async () => {
      renderCart()
      await userEvent.click(screen.getByText('add backpack'))
      expect(screen.getByTestId('item-1')).toHaveTextContent('Backpack — $64.99 × 1')
      expect(screen.getByTestId('item-count')).toHaveTextContent('1')
    })

    it('increases quantity instead of duplicating when the same product is added again', async () => {
      renderCart()
      await userEvent.click(screen.getByText('add backpack'))
      await userEvent.click(screen.getByText('add backpack'))
      expect(screen.getByTestId('item-1')).toHaveTextContent('× 2')
      expect(screen.getAllByTestId(/^item-/)).toHaveLength(1)
    })

    it('caps quantity at available stock', async () => {
      render(
        <CartProvider>
          <CartHarnessWithLowStock />
        </CartProvider>
      )
      await userEvent.click(screen.getByText('add 5 of stock-2-item'))
      expect(screen.getByTestId('item-99')).toHaveTextContent('× 2')
    })

    it('supports multiple different products in the same cart', async () => {
      renderCart()
      await userEvent.click(screen.getByText('add backpack'))
      await userEvent.click(screen.getByText('add bottle'))
      expect(screen.getByTestId('item-count')).toHaveTextContent('2')
      expect(screen.getAllByTestId(/^item-/)).toHaveLength(2)
    })
  })

  describe('removeItem', () => {
    it('removes the specified product only', async () => {
      renderCart()
      await userEvent.click(screen.getByText('add backpack'))
      await userEvent.click(screen.getByText('add bottle'))
      await userEvent.click(screen.getByText('remove backpack'))
      expect(screen.queryByTestId('item-1')).not.toBeInTheDocument()
      expect(screen.getByTestId('item-2')).toBeInTheDocument()
    })
  })

  describe('updateQuantity', () => {
    it('updates the quantity for the specified product', async () => {
      renderCart()
      await userEvent.click(screen.getByText('add backpack'))
      await userEvent.click(screen.getByText('set backpack qty 3'))
      expect(screen.getByTestId('item-1')).toHaveTextContent('× 3')
    })
  })

  describe('syncProduct — regression: cart showed stale price after product edit', () => {
    it('reflects an updated price immediately without re-adding the item', async () => {
      renderCart()
      await userEvent.click(screen.getByText('add backpack'))
      expect(screen.getByTestId('item-1')).toHaveTextContent('$64.99')

      await userEvent.click(screen.getByText('sync backpack price drop'))

      expect(screen.getByTestId('item-1')).toHaveTextContent('$34.99')
      expect(screen.queryByTestId('item-1')).not.toHaveTextContent('$64.99')
    })

    it('recalculates the cart total using the synced price', async () => {
      renderCart()
      await userEvent.click(screen.getByText('add backpack'))
      expect(screen.getByTestId('total')).toHaveTextContent('64.99')

      await userEvent.click(screen.getByText('sync backpack price drop'))

      expect(screen.getByTestId('total')).toHaveTextContent('34.99')
    })

    it('preserves the existing quantity when syncing a price-only change', async () => {
      renderCart()
      await userEvent.click(screen.getByText('add backpack'))
      await userEvent.click(screen.getByText('set backpack qty 3'))

      await userEvent.click(screen.getByText('sync backpack price drop'))

      expect(screen.getByTestId('item-1')).toHaveTextContent('× 3')
    })

    it('clamps quantity down if the synced product now has less stock than the cart quantity', async () => {
      renderCart()
      await userEvent.click(screen.getByText('add backpack'))
      await userEvent.click(screen.getByText('set backpack qty 3'))

      await userEvent.click(screen.getByText('sync backpack with reduced stock'))

      expect(screen.getByTestId('item-1')).toHaveTextContent('× 2')
    })

    it('does nothing if the synced product is not in the cart', async () => {
      renderCart()
      await userEvent.click(screen.getByText('add bottle'))
      await userEvent.click(screen.getByText('sync backpack price drop'))

      expect(screen.queryByTestId('item-1')).not.toBeInTheDocument()
      expect(screen.getByTestId('item-2')).toBeInTheDocument()
    })
  })

  describe('clearCart', () => {
    it('empties the cart entirely', async () => {
      renderCart()
      await userEvent.click(screen.getByText('add backpack'))
      await userEvent.click(screen.getByText('add bottle'))
      await userEvent.click(screen.getByText('clear cart'))

      expect(screen.getByTestId('item-count')).toHaveTextContent('0')
      expect(screen.queryAllByTestId(/^item-/)).toHaveLength(0)
    })
  })

  describe('total', () => {
    it('sums price × quantity across all items', async () => {
      renderCart()
      await userEvent.click(screen.getByText('add backpack'))      // 64.99 × 1
      await userEvent.click(screen.getByText('add bottle'))        // 12.50 × 1
      await userEvent.click(screen.getByText('set backpack qty 3')) // 64.99 × 3 = 194.97

      // 194.97 + 12.50 = 207.47
      expect(screen.getByTestId('total')).toHaveTextContent('207.47')
    })
  })
})

function CartHarnessWithLowStock() {
  const { items, addItem } = useCart()
  return (
    <div>
      <ul>
        {items.map(i => (
          <li key={i.product.id} data-testid={`item-${i.product.id}`}>
            {i.product.name} × {i.quantity}
          </li>
        ))}
      </ul>
      <button onClick={() => addItem({ id: 99, name: 'Stock-2 Item', price: 9.99, stock: 2 }, 5)}>
        add 5 of stock-2-item
      </button>
    </div>
  )
}
