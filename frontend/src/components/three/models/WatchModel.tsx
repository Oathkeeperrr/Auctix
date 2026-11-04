import { useMemo, useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';

/**
 * A luxury dive watch built entirely from primitives: case, ceramic bezel, dial with
 * applied markers, sapphire crystal, crown and a tapering bracelet.
 * No external model is downloaded, so the hero works offline and loads instantly.
 */
export function WatchModel({ accent = '#d8b35f' }: { accent?: string }) {
  const handsRef = useRef<THREE.Group>(null);

  const markers = useMemo(
    () =>
      Array.from({ length: 12 }, (_, index) => {
        const angle = (index / 12) * Math.PI * 2;
        return {
          key: index,
          position: [Math.sin(angle) * 0.62, Math.cos(angle) * 0.62, 0.085] as [number, number, number],
          rotation: [0, 0, -angle] as [number, number, number],
          major: index % 3 === 0,
        };
      }),
    [],
  );

  const links = useMemo(
    () =>
      Array.from({ length: 12 }, (_, index) => {
        const step = index + 1;
        const direction = index < 6 ? 1 : -1;
        const offset = (index % 6) + 1;
        const drop = offset * 0.015 * offset;
        return {
          key: step,
          position: [0, direction * (0.92 + offset * 0.17), -drop] as [number, number, number],
          rotation: [direction * offset * 0.09, 0, 0] as [number, number, number],
          width: 0.62 - offset * 0.028,
        };
      }),
    [],
  );

  // A real watch runs: the seconds hand sweeps so the object never looks like a still image.
  useFrame((state) => {
    if (handsRef.current) {
      handsRef.current.rotation.z = -state.clock.elapsedTime * 0.35;
    }
  });

  return (
    <group>
      {/* Bracelet links, tapering away from the case */}
      {links.map((link) => (
        <mesh key={link.key} position={link.position} rotation={link.rotation} castShadow>
          <boxGeometry args={[link.width, 0.14, 0.09]} />
          <meshStandardMaterial color="#c9ced8" metalness={1} roughness={0.26} />
        </mesh>
      ))}

      {/* Lugs */}
      {[
        [-0.34, 0.92, 0],
        [0.34, 0.92, 0],
        [-0.34, -0.92, 0],
        [0.34, -0.92, 0],
      ].map((position, index) => (
        <mesh key={`lug-${index}`} position={position as [number, number, number]} castShadow>
          <boxGeometry args={[0.16, 0.3, 0.16]} />
          <meshStandardMaterial color="#d2d7e0" metalness={1} roughness={0.22} />
        </mesh>
      ))}

      {/* Case */}
      <mesh rotation={[Math.PI / 2, 0, 0]} castShadow receiveShadow>
        <cylinderGeometry args={[0.95, 0.9, 0.26, 72]} />
        <meshStandardMaterial color="#d7dce5" metalness={1} roughness={0.2} />
      </mesh>

      {/* Ceramic bezel with a gold insert ring */}
      <mesh position={[0, 0, 0.07]} castShadow>
        <torusGeometry args={[0.86, 0.1, 24, 80]} />
        <meshStandardMaterial color="#0a0e1a" metalness={0.65} roughness={0.32} />
      </mesh>
      <mesh position={[0, 0, 0.1]}>
        <torusGeometry args={[0.86, 0.016, 16, 80]} />
        <meshStandardMaterial
          color={accent}
          metalness={1}
          roughness={0.16}
          emissive={accent}
          emissiveIntensity={0.18}
        />
      </mesh>

      {/* Dial */}
      <mesh position={[0, 0, 0.055]} rotation={[Math.PI / 2, 0, 0]}>
        <cylinderGeometry args={[0.78, 0.78, 0.04, 64]} />
        <meshStandardMaterial color="#05070f" metalness={0.45} roughness={0.42} />
      </mesh>

      {/* Applied hour markers */}
      {markers.map((marker) => (
        <mesh key={marker.key} position={marker.position} rotation={marker.rotation}>
          <boxGeometry args={marker.major ? [0.075, 0.15, 0.025] : [0.05, 0.1, 0.022]} />
          <meshStandardMaterial
            color="#f3f0e4"
            emissive="#9fe8d6"
            emissiveIntensity={0.55}
            metalness={0.3}
            roughness={0.3}
          />
        </mesh>
      ))}

      {/* Hands */}
      <group ref={handsRef} position={[0, 0, 0.095]}>
        <mesh position={[0, 0.2, 0]}>
          <boxGeometry args={[0.045, 0.44, 0.018]} />
          <meshStandardMaterial color="#f5f3ea" emissive="#8fe3d0" emissiveIntensity={0.35} />
        </mesh>
        <mesh position={[0.16, -0.06, -0.01]} rotation={[0, 0, -1.1]}>
          <boxGeometry args={[0.035, 0.62, 0.016]} />
          <meshStandardMaterial color="#e8e6dc" emissive="#8fe3d0" emissiveIntensity={0.25} />
        </mesh>
        <mesh position={[0, 0, 0.02]} rotation={[Math.PI / 2, 0, 0]}>
          <cylinderGeometry args={[0.045, 0.045, 0.05, 20]} />
          <meshStandardMaterial color={accent} metalness={1} roughness={0.15} />
        </mesh>
      </group>

      {/* Sapphire crystal */}
      <mesh position={[0, 0, 0.12]} rotation={[Math.PI / 2, 0, 0]}>
        <cylinderGeometry args={[0.82, 0.86, 0.07, 64]} />
        <meshPhysicalMaterial
          color="#ffffff"
          transparent
          opacity={0.18}
          roughness={0.02}
          metalness={0}
          transmission={0.92}
          thickness={0.3}
          ior={1.62}
        />
      </mesh>

      {/* Crown */}
      <mesh position={[0.99, 0, 0]} rotation={[0, 0, Math.PI / 2]} castShadow>
        {/* A cylinder runs along Y, so this turns the crown to stick out sideways. */}
        <cylinderGeometry args={[0.1, 0.1, 0.14, 24]} />
        <meshStandardMaterial color={accent} metalness={1} roughness={0.2} />
      </mesh>
    </group>
  );
}
