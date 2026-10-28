import { useEffect, useState, type FormEvent } from 'react';
import { PageHeader, PageShell } from '../components/layout/PageHeader';
import { Avatar } from '../components/ui/Avatar';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Input, Textarea } from '../components/ui/Field';
import { ApiError, api } from '../lib/api';
import { dateTime, humanise } from '../lib/format';
import { useAuthStore } from '../store/authStore';
import { toast } from '../store/toastStore';

export function ProfilePage() {
  const user = useAuthStore((state) => state.user);
  const setUser = useAuthStore((state) => state.setUser);

  const [fullName, setFullName] = useState('');
  const [bio, setBio] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [savingProfile, setSavingProfile] = useState(false);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [savingPassword, setSavingPassword] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  useEffect(() => {
    if (!user) return;
    setFullName(user.fullName);
    setBio(user.bio ?? '');
    setAvatarUrl(user.avatarUrl ?? '');
  }, [user]);

  if (!user) return null;

  async function saveProfile(event: FormEvent) {
    event.preventDefault();
    setSavingProfile(true);
    try {
      const updated = await api.updateProfile({ fullName, bio, avatarUrl });
      setUser(updated);
      toast.success('Profile updated');
    } catch (error) {
      toast.error('Could not save', error instanceof ApiError ? error.message : undefined);
    } finally {
      setSavingProfile(false);
    }
  }

  async function savePassword(event: FormEvent) {
    event.preventDefault();
    setPasswordError(null);
    if (newPassword !== confirmPassword) {
      setPasswordError('The two new passwords do not match.');
      return;
    }
    if (newPassword.length < 8) {
      setPasswordError('Use at least 8 characters.');
      return;
    }
    setSavingPassword(true);
    try {
      await api.changePassword(currentPassword, newPassword);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      toast.success('Password changed');
    } catch (error) {
      const message = error instanceof ApiError ? error.message : 'Could not change your password.';
      setPasswordError(message);
    } finally {
      setSavingPassword(false);
    }
  }

  return (
    <PageShell>
      <PageHeader eyebrow="Account" title="Your profile" />

      <div className="grid gap-5 lg:grid-cols-[1fr_1.3fr]">
        <div className="glass h-fit space-y-4 p-6 text-center">
          <Avatar name={user.fullName} src={avatarUrl || user.avatarUrl} size={80} className="mx-auto" />
          <div>
            <h2 className="font-display text-lg font-semibold text-white">{user.fullName}</h2>
            <p className="text-[13px] text-slate-500">{user.email}</p>
          </div>
          <div className="flex justify-center gap-2">
            <Badge tone="violet">{humanise(user.role)}</Badge>
            <Badge tone={user.status === 'ACTIVE' ? 'live' : 'danger'}>{humanise(user.status)}</Badge>
          </div>
          <p className="border-t border-white/[0.07] pt-3 text-[11px] text-slate-600">
            Member since {dateTime(user.createdAt)}
          </p>
        </div>

        <div className="space-y-5">
          <form onSubmit={saveProfile} className="glass space-y-4 p-5 sm:p-6">
            <h2 className="text-[13px] font-semibold uppercase tracking-[0.16em] text-slate-400">
              Profile details
            </h2>
            <Input
              label="Full name"
              value={fullName}
              onChange={(event) => setFullName(event.target.value)}
              maxLength={120}
            />
            <Input
              label="Avatar URL"
              type="url"
              value={avatarUrl}
              onChange={(event) => setAvatarUrl(event.target.value)}
              placeholder="https://…"
            />
            <Textarea
              label="Bio"
              value={bio}
              maxLength={400}
              hint={`${bio.length}/400`}
              onChange={(event) => setBio(event.target.value)}
              placeholder="Collector of vintage dive watches."
            />
            <Button type="submit" loading={savingProfile}>
              Save changes
            </Button>
          </form>

          <form onSubmit={savePassword} className="glass space-y-4 p-5 sm:p-6">
            <h2 className="text-[13px] font-semibold uppercase tracking-[0.16em] text-slate-400">
              Change password
            </h2>
            <Input
              label="Current password"
              type="password"
              autoComplete="current-password"
              required
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                label="New password"
                type="password"
                autoComplete="new-password"
                required
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
              />
              <Input
                label="Confirm new password"
                type="password"
                autoComplete="new-password"
                required
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
              />
            </div>
            {passwordError && (
              <p role="alert" className="rounded-lg bg-danger/[0.1] px-3 py-2 text-[12px] text-danger">
                {passwordError}
              </p>
            )}
            <Button type="submit" variant="secondary" loading={savingPassword}>
              Update password
            </Button>
          </form>
        </div>
      </div>
    </PageShell>
  );
}
