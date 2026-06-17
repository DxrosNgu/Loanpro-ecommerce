import { Routes, Route } from 'react-router-dom'
import Navbar from './components/Navbar'
import ProductListPage from './pages/ProductListPage'
import ProductDetailPage from './pages/ProductDetailPage'
import ProductFormPage from './pages/ProductFormPage'
import CsvImportPage from './pages/CsvImportPage'
import OrderConfirmPage from './pages/OrderConfirmPage'

export default function App() {
  return (
    <div className="min-h-screen bg-surface">
      <Navbar />
      <main className="max-w-6xl mx-auto px-4 py-8">
        <Routes>
          <Route path="/"                   element={<ProductListPage />} />
          <Route path="/products/new"       element={<ProductFormPage />} />
          <Route path="/products/import"    element={<CsvImportPage />} />
          <Route path="/products/:id/edit"  element={<ProductFormPage />} />
          <Route path="/products/:id"       element={<ProductDetailPage />} />
          <Route path="/orders/:id"         element={<OrderConfirmPage />} />
        </Routes>
      </main>
    </div>
  )
}
