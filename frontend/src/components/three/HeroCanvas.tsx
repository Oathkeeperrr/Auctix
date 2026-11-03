import { Canvas } from '@react-three/fiber';
import { AdaptiveDpr, PerspectiveCamera, Preload } from '@react-three/drei';
import { useDeviceCapability } from '../../hooks/useDeviceCapability';
import type { ModelKind } from '../../types';
import { AuctionStage } from './AuctionStage';
import { StaticStage } from './StaticStage';

/**
 * The cinematic hero scene.
 *
 * Falls back to a styled 2D presentation whenever WebGL is unavailable or the visitor has
 * asked for reduced motion, so the page is never broken and never moves against their wishes.
 */
export function HeroCanvas({ kind, accent }: { kind: ModelKind; accent?: string }) {
  const { tier, dpr } = useDeviceCapability();

  if (tier === 'none') {
    return <StaticStage kind={kind} />;
  }

  return (
    <Canvas
      dpr={dpr}
      shadows={tier === 'high'}
      gl={{ antialias: tier !== 'low', alpha: true, powerPreference: 'high-performance' }}
      className="!absolute inset-0"
      aria-label="Interactive 3D display of the featured auction item"
    >
      <PerspectiveCamera makeDefault position={[0, 0.9, 6.4]} fov={38} />
      <fog attach="fog" args={['#04050a', 9, 18]} />
      <AuctionStage kind={kind} tier={tier} accent={accent} />
      <AdaptiveDpr pixelated />
      <Preload all />
    </Canvas>
  );
}
