import type { WorkspaceNotice } from './live/useWorkspaceLive';

const ICONS: Record<WorkspaceNotice['tone'], string> = { replan: '↻', success: '✓', danger: '!' };

interface NoticeBannerProps {
  notice: WorkspaceNotice;
  onDismiss: () => void;
}

export function NoticeBanner({ notice, onDismiss }: NoticeBannerProps) {
  return (
    <div className={`banner banner-${notice.tone}`} role={notice.tone === 'danger' ? 'alert' : 'status'} key={notice.id}>
      <span className="banner-icon" aria-hidden="true">
        {ICONS[notice.tone]}
      </span>
      <span className="banner-message">{notice.message}</span>
      <button type="button" className="icon-button" aria-label="Dismiss" onClick={onDismiss}>
        ×
      </button>
    </div>
  );
}
