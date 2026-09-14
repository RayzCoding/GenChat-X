import { BrowserRouter, Navigate, Route, Routes, useParams } from 'react-router-dom'
import { HistoryPage } from './components/history/HistoryPage'
import { WorkbenchPage } from './components/workbench/WorkbenchPage'
import { parseAgentMode, workbenchPath } from './utils/agentMode'

function LegacyRedirect({ mode }: { mode?: string }) {
  const { conversationId } = useParams<{ conversationId?: string }>()
  return <Navigate to={workbenchPath(conversationId, parseAgentMode(mode))} replace />
}

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/history" element={<HistoryPage />} />
        <Route path="/c/:conversationId?" element={<WorkbenchPage />} />
        <Route path="/" element={<Navigate to="/c" replace />} />
        <Route path="/chat/:conversationId?" element={<LegacyRedirect mode="chat" />} />
        <Route path="/file-qa" element={<LegacyRedirect mode="file" />} />
        <Route path="/ppt/:conversationId?" element={<LegacyRedirect mode="ppt" />} />
        <Route path="/deep-research" element={<LegacyRedirect mode="deep" />} />
        <Route path="/skills/:conversationId?" element={<LegacyRedirect mode="skills" />} />
        <Route path="*" element={<Navigate to="/c" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
