import { useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';

/** A faceted crystal on a plinth: the catch-all display object for collectibles. */
export function CollectibleModel({ accent = '#8b5cf6' }: { accent?: string }) {
  const gemRef = useRef<THREE.Mesh>(null);
  const haloRef = useRef<THREE.Mesh>(null);

  useFrame((state, delta) => {
    if (gemRef.current) {
      gemRef.current.rotation.y += delta * 0.5;
      gemRef.current.position.y = 0.42 + Math.sin(state.clock.elapsedTime * 1.1) * 0.06;
    }
    if (haloRef.current) {
      haloRef.current.rotation.z += delta * 0.25;
    }
  });

  return (
    <group>
      <mesh ref={gemRef} position={[0, 0.42, 0]} castShadow>
        <icosahedronGeometry args={[0.78, 0]} />
        <meshPhysicalMaterial
          color={accent}
          metalness={0.25}
          roughness={0.05}
          transmission={0.75}
          thickness={1.1}
          ior={2.1}
          iridescence={0.8}
          iridescenceIOR={1.6}
          clearcoat={1}
        />
      </mesh>

      <mesh ref={haloRef} position={[0, 0.42, 0]} rotation={[Math.PI / 2.4, 0, 0]}>
        <torusGeometry args={[1.08, 0.012, 12, 90]} />
        <meshStandardMaterial color="#d8b35f" emissive="#d8b35f" emissiveIntensity={1.6} toneMapped={false} />
      </mesh>

      {/* Plinth */}
      <mesh position={[0, -0.5, 0]} castShadow receiveShadow>
        <cylinderGeometry args={[0.52, 0.64, 0.42, 6]} />
        <meshStandardMaterial color="#141a2b" metalness={0.85} roughness={0.3} />
      </mesh>
      <mesh position={[0, -0.28, 0]}>
        <cylinderGeometry args={[0.54, 0.54, 0.03, 6]} />
        <meshStandardMaterial color="#d8b35f" metalness={1} roughness={0.2} />
      </mesh>

      <pointLight position={[0, 0.5, 0]} intensity={4} distance={3.2} color={accent} />
    </group>
  );
}
