import { useMemo } from 'react';
import * as THREE from 'three';

/**
 * A framed abstract canvas. The painting is generated into a 2D canvas at runtime, so the
 * artwork is original to this app and nothing is fetched over the network.
 */
function usePaintingTexture(): THREE.CanvasTexture | null {
  return useMemo(() => {
    if (typeof document === 'undefined') return null;
    const size = 512;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d');
    if (!ctx) return null;

    const night = ctx.createLinearGradient(0, 0, size, size);
    night.addColorStop(0, '#060b24');
    night.addColorStop(0.45, '#16307a');
    night.addColorStop(1, '#0a1434');
    ctx.fillStyle = night;
    ctx.fillRect(0, 0, size, size);

    // Swirling brush strokes.
    for (let i = 0; i < 160; i += 1) {
      const cx = Math.random() * size;
      const cy = Math.random() * size * 0.8;
      const radius = 14 + Math.random() * 80;
      const sweep = 1.2 + Math.random() * 3.2;
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, sweep);
      ctx.strokeStyle = `hsla(${210 + Math.random() * 40}, ${55 + Math.random() * 35}%, ${
        45 + Math.random() * 35
      }%, ${0.12 + Math.random() * 0.3})`;
      ctx.lineWidth = 2 + Math.random() * 7;
      ctx.lineCap = 'round';
      ctx.stroke();
    }

    // Warm highlights, like lamplight in the distance.
    for (let i = 0; i < 22; i += 1) {
      const cx = Math.random() * size;
      const cy = size * 0.55 + Math.random() * size * 0.4;
      const glow = ctx.createRadialGradient(cx, cy, 0, cx, cy, 10 + Math.random() * 26);
      glow.addColorStop(0, 'rgba(255, 221, 150, 0.85)');
      glow.addColorStop(1, 'rgba(255, 200, 120, 0)');
      ctx.fillStyle = glow;
      ctx.fillRect(cx - 40, cy - 40, 80, 80);
    }

    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.needsUpdate = true;
    return texture;
  }, []);
}

export function ArtworkModel({ accent = '#d8b35f' }: { accent?: string }) {
  const painting = usePaintingTexture();

  return (
    <group rotation={[0, -0.18, 0]}>
      {/* Canvas */}
      <mesh position={[0, 0, 0.03]} castShadow receiveShadow>
        <boxGeometry args={[2.1, 1.6, 0.06]} />
        {painting ? (
          <meshStandardMaterial map={painting} roughness={0.85} metalness={0.02} />
        ) : (
          <meshStandardMaterial color="#16307a" roughness={0.85} />
        )}
      </mesh>

      {/* Gilded frame */}
      {[
        { args: [2.42, 0.17, 0.17], position: [0, 0.87, 0] },
        { args: [2.42, 0.17, 0.17], position: [0, -0.87, 0] },
        { args: [0.17, 1.92, 0.17], position: [-1.13, 0, 0] },
        { args: [0.17, 1.92, 0.17], position: [1.13, 0, 0] },
      ].map((bar, index) => (
        <mesh key={index} position={bar.position as [number, number, number]} castShadow>
          <boxGeometry args={bar.args as [number, number, number]} />
          <meshStandardMaterial
            color={accent}
            metalness={1}
            roughness={0.28}
            emissive={accent}
            emissiveIntensity={0.08}
          />
        </mesh>
      ))}

      {/* Inner fillet */}
      <mesh position={[0, 0, 0.055]}>
        <boxGeometry args={[2.18, 1.68, 0.02]} />
        <meshStandardMaterial color="#1a1d26" metalness={0.4} roughness={0.6} />
      </mesh>

      {/* Picture light */}
      <mesh position={[0, 1.08, 0.28]} rotation={[0.6, 0, 0]}>
        <cylinderGeometry args={[0.05, 0.07, 0.5, 16]} />
        <meshStandardMaterial color={accent} metalness={0.9} roughness={0.3} />
      </mesh>
      <pointLight position={[0, 1.0, 0.7]} intensity={6} distance={4} color="#ffe6b8" />
    </group>
  );
}
