import { Canvas } from '@react-three/fiber';
import { AdaptiveDpr, OrbitControls, PerspectiveCamera } from '@react-three/drei';
import { useDeviceCapability } from '../../hooks/useDeviceCapability';
import type { ModelKind } from '../../types';
import { AuctionStage } from './AuctionStage';
import { StaticStage } from './StaticStage';

/**
 * The inspect-it-yourself viewer on the auction detail page. Smaller scene, orbit controls,
 * no auto-spin fight with the user's drag.
 */
export function ProductViewer({ kind, accent }: { kind: ModelKind; accent?: string }) {
  const { tier, dpr, isTouch } = useDeviceCapability();

  if (tier === 'none') {
    return <StaticStage kind={kind} />;
  }

  return (
    <Canvas
      dpr={dpr}
      shadows={tier === 'high'}
      gl={{ antialias: tier !== 'low', alpha: true }}
      className="!absolute inset-0"
      aria-label="Rotatable 3D view of this lot"
    >
      <PerspectiveCamera makeDefault position={[0, 0.6, 6]} fov={40} />
      <AuctionStage kind={kind} tier={tier} accent={accent} interactive={false} />
      <OrbitControls
        enablePan={false}
        enableZoom={!isTouch}
        minDistance={3.6}
        maxDistance={9}
        minPolarAngle={Math.PI / 6}
        maxPolarAngle={Math.PI / 1.9}
        autoRotate
        autoRotateSpeed={0.7}
        dampingFactor={0.08}
      />
      <AdaptiveDpr pixelated />
    </Canvas>
  );
}
