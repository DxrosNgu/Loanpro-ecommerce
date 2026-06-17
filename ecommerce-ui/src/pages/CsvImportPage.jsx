import { useState, useRef } from 'react'
import { Link } from 'react-router-dom'
import { productsApi } from '../api/client'

export default function CsvImportPage() {
  const [file, setFile] = useState(null)
  const [dragging, setDragging] = useState(false)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const inputRef = useRef()

  function handleFile(f) {
    if (!f) return
    if (!f.name.endsWith('.csv')) { setError('Only .csv files are accepted'); return }
    setFile(f); setResult(null); setError(null)
  }

  async function handleUpload() {
    if (!file) return
    setLoading(true); setError(null); setResult(null)
    try {
      const data = await productsApi.import(file)
      setResult(data)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  const onDrop = e => {
    e.preventDefault(); setDragging(false)
    handleFile(e.dataTransfer.files[0])
  }

  return (
    <>
      <div className="mb-6 flex items-center gap-2 text-sm text-gray-500">
        <Link to="/" className="hover:text-brand-600">Products</Link>
        <span>/</span>
        <span className="text-gray-900 font-medium">Import from CSV</span>
      </div>

      <div className="max-w-2xl space-y-5">

        {/* Drop zone */}
        <div
          onDragOver={e => { e.preventDefault(); setDragging(true) }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
          onClick={() => inputRef.current.click()}
          className={`card p-10 flex flex-col items-center justify-center gap-3 cursor-pointer border-2 border-dashed transition-colors
            ${dragging ? 'border-brand-400 bg-brand-50' : 'border-gray-200 hover:border-brand-300 hover:bg-gray-50'}`}
        >
          <input
            ref={inputRef} type="file" accept=".csv"
            className="hidden"
            onChange={e => handleFile(e.target.files[0])}
          />
          <svg width="36" height="36" viewBox="0 0 24 24" fill="none"
            stroke={dragging ? '#4361ee' : '#9ca3af'} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          {file ? (
            <div className="text-center">
              <p className="font-medium text-gray-900">{file.name}</p>
              <p className="text-xs text-gray-400 mt-0.5">{(file.size / 1024).toFixed(1)} KB — click to change</p>
            </div>
          ) : (
            <div className="text-center">
              <p className="font-medium text-gray-700">Drop your CSV here, or click to browse</p>
              <p className="text-xs text-gray-400 mt-1">Columns: name · sku · description · category · price · stock · weight_kg</p>
            </div>
          )}
        </div>

        {/* Expected format hint */}
        <div className="card p-4">
          <p className="text-xs font-medium text-gray-500 mb-2 uppercase tracking-wide">Expected format</p>
          <pre className="text-[11px] font-mono text-gray-600 overflow-x-auto">
{`name,sku,description,category,price,stock,weight_kg
Running Shoes,RS-001,Lightweight training shoe,Footwear,89.99,150,0.35`}
          </pre>
          <div className="mt-3 flex flex-wrap gap-2">
            {['$ in price is stripped','free → 0.00','XSS is sanitised','Duplicate SKU → upsert','Blank name → rejected','Negative stock → rejected'].map(h => (
              <span key={h} className="badge-gray text-[10px]">{h}</span>
            ))}
          </div>
        </div>

        <div className="flex gap-2">
          <button
            onClick={handleUpload}
            disabled={!file || loading}
            className="btn-primary"
          >
            {loading ? (
              <>
                <svg className="animate-spin w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>
                </svg>
                Importing…
              </>
            ) : 'Upload & import'}
          </button>
          {result && (
            <Link to="/" className="btn-secondary">
              View products
            </Link>
          )}
        </div>

        {error && (
          <div className="bg-red-50 border border-red-100 text-red-700 text-sm rounded-lg p-4">
            {error}
          </div>
        )}

        {/* Results */}
        {result && (
          <div className="card p-5 space-y-4">
            {/* Summary row */}
            <div className="flex gap-4 flex-wrap">
              <div className="flex items-center gap-2">
                <div className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
                <span className="text-sm font-medium text-emerald-700">{result.imported} imported</span>
              </div>
              {result.updated > 0 && (
                <div className="flex items-center gap-2">
                  <div className="w-2.5 h-2.5 rounded-full bg-blue-500" />
                  <span className="text-sm font-medium text-blue-700">{result.updated} updated</span>
                </div>
              )}
              {result.skipped > 0 && (
                <div className="flex items-center gap-2">
                  <div className="w-2.5 h-2.5 rounded-full bg-gray-400" />
                  <span className="text-sm font-medium text-gray-500">{result.skipped} skipped</span>
                </div>
              )}
              {result.errors?.length > 0 && (
                <div className="flex items-center gap-2">
                  <div className="w-2.5 h-2.5 rounded-full bg-red-500" />
                  <span className="text-sm font-medium text-red-700">{result.errors.length} rejected</span>
                </div>
              )}
            </div>

            {/* Error rows */}
            {result.errors?.length > 0 && (
              <div>
                <p className="text-xs font-medium text-gray-500 uppercase tracking-wide mb-2">Rejected rows</p>
                <div className="space-y-1.5">
                  {result.errors.map((e, i) => (
                    <div key={i} className="flex items-start gap-3 text-sm bg-red-50 rounded-lg px-3 py-2">
                      <span className="text-red-400 font-mono text-xs mt-0.5">row {e.row}</span>
                      {e.sku && <span className="font-mono text-xs text-gray-500 mt-0.5">{e.sku}</span>}
                      <span className="text-red-700">{e.reason}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </>
  )
}
