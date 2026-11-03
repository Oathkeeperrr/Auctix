import { Suspense, useMemo, useRef } from 'react';
import { Environment, Lightformer, MeshReflectorMaterial, Float, ContactShadows } from '@react-three/drei';
import { useFrame, type GroupProps } from '@react-three/fiber';
import * as THREE from 'three';
import type { ModelKind } from '../../types';
import type { QualityTier } from '../../hooks/useDeviceCapability';
import { ArtworkModel } from './models/ArtworkModel';
import { CarModel } from './models/CarModel';
import { CollectibleModel } from './models/CollectibleModel';
import { SneakerModel } from './models/SneakerModel';
import { WatchModel } from './models/WatchModel';

/** Picks the procedural model for an item and scales it to sit nicely on the pedestal. */
export function AuctionObject({ kind, accent }: { kind: ModelKind; accent?: string }) {
  switch (kind) {
    case 'WATCH':
      return (
        <group scale={1.05} rotation={[-0.22, 0, 0]}>
          <WatchModel accent={accent ?? '#d8b35f'} />
        </group>
      );
    case 'CAR':
      return (
        <group scale={0.78} position={[0, -0.1, 0]}>
          <CarModel accent={accent ?? '#4c7dff'} />
        </group>
      );
    case 'SNEAKER':
      return (
        <group scale={0.82} position={[0, 0.1, 0]}>
          <SneakerModel accent={accent ?? '#ff5f6d'} />
        </group>
      );
    case 'ARTWORK':
      return (
        <group scale={0.95} position={[0, 0.1, 0]}>
          <ArtworkModel accent={accent ?? '#d8b35f'} />
        </group>
      );
    case 'COLLECTIBLE':
    default:
      return (
        <group scale={1.1}>
          <CollectibleModel accent={accent ?? '#8b5cf6'} />
        </group>
      );
  }
}

/** Slow idle rotation plus a gentle tilt that follows the pointer. */
function PointerRig({
  children,
  spin = 0.18,
  tilt = 0.22,
  ...props
}: GroupProps & { spin?: number; tilt?: number }) {
  const group = useRef<THREE.Group>(null);

  useFrame((state, delta) => {
    const node = group.current;
    if (!node) return;
    node.rotation.y += delta * spin;
    const targetX = -state.pointer.y * tilt;
    const targetZ = state.pointer.x * tilt * 0.35;
    node.rotation.x = THREE.MathUtils.lerp(node.rotation.x, targetX, 0.06);
    node.rotation.z = THREE.MathUtils.lerp(node.rotation.z, targetZ, 0.06);
  });

  return (
    <group ref={group} {...props}>
      {children}
    </group>
  );
}

/** The futuristic display plinth with its animated glow ring. */
export function Pedestal({ tier }: { tier: QualityTier }) {
  const ring = useRef<THREE.Mesh>(null);
  const halo = useRef<THREE.Mesh>(null);

  useFrame((state) => {
    const pulse = 0.55 + Math.sin(state.clock.elapsedTime * 1.4) * 0.25;
    if (ring.current) {
      const material = ring.current.material as THREE.MeshStandardMaterial;
      material.emissiveIntensity = 1.6 + pulse;
    }
    if (halo.current) {
      halo.current.rotation.z = state.clock.elapsedTime * 0.2;
      halo.current.scale.setScalar(1 + Math.sin(state.clock.elapsedTime * 1.1) * 0.02);
    }
  });

  return (
    <group position={[0, -1.55, 0]}>
      {/* Reflective gallery floor */}
      {tier !== 'low' ? (
        <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.26, 0]} receiveShadow>
          <circleGeometry args={[9, 64]} />
          <MeshReflectorMaterial
            mirror={0.55}
            blur={[320, 90]}
            resolution={tier === 'high' ? 1024 : 512}
            mixBlur={1.1}
            mixStrength={28}
            depthScale={1.1}
            minDepthThreshold={0.4}
            maxDepthThreshold={1.3}
            color="#05070f"
            metalness={0.75}
            roughness={0.92}
          />
        </mesh>
      ) : (
        <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.26, 0]} receiveShadow>
          <circleGeometry args={[9, 48]} />
          <meshStandardMaterial color="#060912" metalness={0.5} roughness={0.75} />
        </mesh>
      )}

      {/* Plinth body */}
      <mesh position={[0, -0.12, 0]} castShadow receiveShadow>
        <cylinderGeometry args={[1.42, 1.62, 0.28, 72]} />
        <meshStandardMaterial color="#0b1122" metalness={0.9} roughness={0.28} />
      </mesh>

      {/* Glowing rim */}
      <mesh ref={ring} position={[0, 0.04, 0]} rotation={[Math.PI / 2, 0, 0]}>
        <torusGeometry args={[1.43, 0.022, 14, 110]} />
        <meshStandardMaterial
          color="#6f9bff"
          emissive="#4c7dff"
          emissiveIntensity={2.2}
          toneMapped={false}
        />
      </mesh>

      {/* Rotating halo above the base */}
      <mesh ref={halo} position={[0, 0.1, 0]} rotation={[Math.PI / 2, 0, 0]}>
        <ringGeometry args={[1.56, 1.62, 96]} />
        <meshBasicMaterial color="#8b5cf6" transparent opacity={0.34} side={THREE.DoubleSide} />
      </mesh>

      <pointLight position={[0, 0.4, 0]} intensity={8} distance={4.5} color="#4c7dff" />
    </group>
  );
}

/** Slow-drifting dust motes that give the gallery depth. */
export function Particles({ count = 320, radius = 7 }: { count?: number; radius?: number }) {
  const points = useRef<THREE.Points>(null);

  const geometry = useMemo(() => {
    const positions = new Float32Array(count * 3);
    for (let i = 0; i < count; i += 1) {
      const angle = Math.random() * Math.PI * 2;
      const distance = Math.sqrt(Math.random()) * radius;
      positions[i * 3] = Math.cos(angle) * distance;
      positions[i * 3 + 1] = Math.random() * 7 - 2;
      positions[i * 3 + 2] = Math.sin(angle) * distance;
    }
    const buffer = new THREE.BufferGeometry();
    buffer.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    return buffer;
  }, [count, radius]);

  useFrame((state, delta) => {
    const node = points.current;
    if (!node) return;
    node.rotation.y += delta * 0.022;
    const attribute = node.geometry.getAttribute('position') as THREE.BufferAttribute;
    const array = attribute.array as Float32Array;
    for (let i = 1; i < array.length; i += 3) {
      array[i] += delta * 0.12;
      if (array[i] > 5) array[i] = -2;
    }
    attribute.needsUpdate = true;
    const material = node.material as THREE.PointsMaterial;
    material.opacity = 0.38 + Math.sin(state.clock.elapsedTime * 0.6) * 0.08;
  });

  return (
    <points ref={points} geometry={geometry}>
      <pointsMaterial
        size={0.035}
        color="#9ec0ff"
        transparent
        opacity={0.42}
        sizeAttenuation
        depthWrite={false}
        blending={THREE.AdditiveBlending}
      />
    </points>
  );
}

/** Volumetric-feeling key/rim lighting plus a local environment for metal reflections. */
export function GalleryLighting({ tier }: { tier: QualityTier }) {
  return (
    <>
      <ambientLight intensity={0.35} />
      <spotLight
        position={[4.5, 7, 4]}
        angle={0.42}
        penumbra={0.9}
        intensity={110}
        color="#cddcff"
        castShadow={tier === 'high'}
        shadow-mapSize={[1024, 1024]}
        shadow-bias={-0.0004}
      />
      <spotLight position={[-5, 4, -3]} angle={0.6} penumbra={1} intensity={60} color="#8b5cf6" />
      <pointLight position={[0, -1, 4]} intensity={18} color="#d8b35f" distance={9} />

      {/* Built in-process from light cards: no HDR file is fetched. */}
      <Environment resolution={tier === 'high' ? 256 : 128}>
        <Lightformer form="rect" intensity={2.2} color="#9bb8ff" scale={[8, 3, 1]} position={[0, 4, -5]} />
        <Lightformer form="rect" intensity={1.4} color="#ffffff" scale={[5, 5, 1]} position={[-5, 2, 2]} />
        <Lightformer form="circle" intensity={1.8} color="#d8b35f" scale={[3, 3, 1]} position={[5, 1, 3]} />
        <Lightformer form="ring" intensity={1.1} color="#8b5cf6" scale={[6, 6, 1]} position={[0, -3, 2]} />
      </Environment>
    </>
  );
}

/**
 * The full stage: lighting, pedestal, the featured object and atmosphere.
 * Everything inside is procedural, so there is nothing to download and no loading cliff.
 */
export function AuctionStage({
  kind,
  tier,
  accent,
  interactive = true,
}: {
  kind: ModelKind;
  tier: QualityTier;
  accent?: string;
  interactive?: boolean;
}) {
  return (
    <Suspense fallback={null}>
      <GalleryLighting tier={tier} />
      <Pedestal tier={tier} />

      <Float
        speed={interactive ? 1.4 : 0}
        rotationIntensity={interactive ? 0.18 : 0}
        floatIntensity={interactive ? 0.5 : 0}
        floatingRange={[-0.08, 0.12]}
      >
        <PointerRig position={[0, 0.25, 0]} spin={interactive ? 0.18 : 0}>
          <AuctionObject kind={kind} accent={accent} />
        </PointerRig>
      </Float>

      <ContactShadows
        position={[0, -1.4, 0]}
        opacity={0.55}
        scale={9}
        blur={2.6}
        far={4}
        resolution={tier === 'high' ? 512 : 256}
        color="#000000"
      />

      {tier !== 'low' && <Particles count={tier === 'high' ? 340 : 160} />}
    </Suspense>
  );
}
