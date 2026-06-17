import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import CsvImportPage from '../../pages/CsvImportPage'
import * as client from '../../api/client'

vi.mock('../../api/client', () => ({
  productsApi: { import: vi.fn() }
}))

const renderPage = () =>
  render(<MemoryRouter><CsvImportPage /></MemoryRouter>)

const makeFile = (name = 'products.csv', type = 'text/csv') =>
  new File(['name,sku\nShoe,SH-001'], name, { type })

describe('CsvImportPage', () => {

  beforeEach(() => vi.clearAllMocks())

  describe('initial state', () => {
    it('renders the file drop zone', () => {
      renderPage()
      expect(screen.getByText(/drop your csv here/i)).toBeInTheDocument()
    })

    it('shows the expected column names', () => {
      renderPage()
      expect(screen.getByText(/name.*sku.*description/i)).toBeInTheDocument()
    })

    it('shows edge-case handling hints', () => {
      renderPage()
      expect(screen.getByText(/\$ in price is stripped/i)).toBeInTheDocument()
      expect(screen.getByText(/XSS is sanitised/i)).toBeInTheDocument()
    })

    it('Upload button is disabled when no file selected', () => {
      renderPage()
      expect(screen.getByRole('button', { name: /upload/i })).toBeDisabled()
    })
  })

  describe('file selection', () => {
    it('shows file name after selecting a file', async () => {
      renderPage()
      const input = document.querySelector('input[type="file"]')
      await userEvent.upload(input, makeFile())
      expect(screen.getByText('products.csv')).toBeInTheDocument()
    })

    it('enables Upload button after file is selected', async () => {
      renderPage()
      const input = document.querySelector('input[type="file"]')
      await userEvent.upload(input, makeFile())
      expect(screen.getByRole('button', { name: /upload/i })).not.toBeDisabled()
    })

    it('rejects non-CSV files with an error message', async () => {
      renderPage()
      const input = document.querySelector('input[type="file"]')
      await userEvent.upload(input, makeFile('data.xlsx', 'application/vnd.ms-excel'))
      expect(screen.getByText(/only .csv files/i)).toBeInTheDocument()
    })
  })

  describe('successful import', () => {
    it('shows imported, updated, and skipped counts', async () => {
      client.productsApi.import.mockResolvedValue({
        imported: 45, updated: 3, skipped: 2, errors: []
      })
      renderPage()
      const input = document.querySelector('input[type="file"]')
      await userEvent.upload(input, makeFile())
      await userEvent.click(screen.getByRole('button', { name: /upload/i }))

      await waitFor(() => {
        expect(screen.getByText(/45 imported/i)).toBeInTheDocument()
        expect(screen.getByText(/3 updated/i)).toBeInTheDocument()
        expect(screen.getByText(/2 skipped/i)).toBeInTheDocument()
      })
    })

    it('shows a "View products" link after successful import', async () => {
      client.productsApi.import.mockResolvedValue({
        imported: 1, updated: 0, skipped: 0, errors: []
      })
      renderPage()
      const input = document.querySelector('input[type="file"]')
      await userEvent.upload(input, makeFile())
      await userEvent.click(screen.getByRole('button', { name: /upload/i }))

      await waitFor(() => {
        expect(screen.getByRole('link', { name: /view products/i })).toBeInTheDocument()
      })
    })

    it('renders rejected row errors with row number, SKU, and reason', async () => {
      client.productsApi.import.mockResolvedValue({
        imported: 1, updated: 0, skipped: 0,
        errors: [
          { row: 3, sku: 'DL-007', reason: 'Stock is negative (-5)' },
          { row: 6, sku: 'HD-099', reason: 'Name is blank' },
        ]
      })
      renderPage()
      const input = document.querySelector('input[type="file"]')
      await userEvent.upload(input, makeFile())
      await userEvent.click(screen.getByRole('button', { name: /upload/i }))

      await waitFor(() => {
        expect(screen.getByText('DL-007')).toBeInTheDocument()
        expect(screen.getByText('Stock is negative (-5)')).toBeInTheDocument()
        expect(screen.getByText('HD-099')).toBeInTheDocument()
        expect(screen.getByText('Name is blank')).toBeInTheDocument()
      })
    })
  })

  describe('failed import', () => {
    it('shows error message when API call fails', async () => {
      client.productsApi.import.mockRejectedValue(new Error('Server error'))
      renderPage()
      const input = document.querySelector('input[type="file"]')
      await userEvent.upload(input, makeFile())
      await userEvent.click(screen.getByRole('button', { name: /upload/i }))

      await waitFor(() => {
        expect(screen.getByText('Server error')).toBeInTheDocument()
      })
    })
  })
})
