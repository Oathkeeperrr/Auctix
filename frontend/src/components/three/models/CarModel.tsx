import { useRef } from 'react';
import { RoundedBox } from '@react-three/drei';
import { useFrame } from '@react-three/fiber';
import * as THREE from 'three';

/** A low-poly hypercar silhouette: wide body, bubble canopy, rear wing, glowing lights. */
export function CarModel({ accent = '#4c7dff' }: { accent?: string }) {
  const wheelsRef = useRef<THREE.Group>(null);

  useFrame((_, delta) => {
    if (wheelsRef.current) {
      wheelsRef.current.children.forEach((wheel) => {
        wheel.rotation.x -= delta * 1.4;
      });
    }
  });

  const wheelPositions: [number, number, number][] = [
    [-0.82, -0.32, 0.72],
    [0.82, -0.32, 0.72],
    [-0.84, -0.32, -0.78],
    [0.84, -0.32, -0.78],
  ];

  return (
    <group rotation={[0, -0.5, 0]} scale={0.95}>
      {/* Main body */}
      <RoundedBox args={[1.74, 0.46, 3.5]} radius={0.2} smoothness={5} castShadow receiveShadow>
        <meshStandardMaterial color="#1d4ed8" metalness={0.92} roughness={0.22} />
      </RoundedBox>

      {/* Lower splitter */}
      <RoundedBox args={[1.82, 0.14, 3.2]} radius={0.06} smoothness={4} position={[0, -0.3, 0]}>
        <meshStandardMaterial color="#0a0d16" metalness={0.5} roughness={0.55} />
      </RoundedBox>

      {/* Cabin / canopy */}
      <RoundedBox args={[1.2, 0.46, 1.5]} radius={0.22} smoothness={5} position={[0, 0.38, -0.12]}>
        <meshPhysicalMaterial
          color="#0b1426"
          metalness={0.2}
          roughness={0.06}
          transmission={0.6}
          thickness={0.5}
          transparent
          opacity={0.85}
        />
      </RoundedBox>

      {/* Rear wing */}
      <mesh position={[0, 0.5, -1.62]} castShadow>
        <boxGeometry args={[1.66, 0.055, 0.42]} />
        <meshStandardMaterial color="#0c1120" metalness={0.75} roughness={0.35} />
      </mesh>
      {[-0.66, 0.66].map((x) => (
        <mesh key={x} position={[x, 0.3, -1.6]}>
          <boxGeometry args={[0.07, 0.4, 0.2]} />
          <meshStandardMaterial color="#0c1120" metalness={0.7} roughness={0.4} />
        </mesh>
      ))}

      {/* Headlights */}
      {[-0.56, 0.56].map((x) => (
        <mesh key={`head-${x}`} position={[x, 0.08, 1.73]}>
          <boxGeometry args={[0.42, 0.1, 0.06]} />
          <meshStandardMaterial color="#eaf2ff" emissive="#cfe2ff" emissiveIntensity={2.6} toneMapped={false} />
        </mesh>
      ))}

      {/* Tail light bar */}
      <mesh position={[0, 0.14, -1.75]}>
        <boxGeometry args={[1.42, 0.07, 0.05]} />
        <meshStandardMaterial color={accent} emissive="#ff3b5c" emissiveIntensity={2.4} toneMapped={false} />
      </mesh>

      {/* Side intakes */}
      {[-0.9, 0.9].map((x) => (
        <mesh key={`intake-${x}`} position={[x, 0.02, -0.5]} rotation={[0, 0, 0.1]}>
          <boxGeometry args={[0.06, 0.26, 0.9]} />
          <meshStandardMaterial color="#05070e" metalness={0.4} roughness={0.7} />
        </mesh>
      ))}

      <group ref={wheelsRef}>
        {wheelPositions.map((position, index) => (
          <group key={index} position={position} rotation={[0, 0, Math.PI / 2]}>
            <mesh castShadow>
              <cylinderGeometry args={[0.37, 0.37, 0.26, 28]} />
              <meshStandardMaterial color="#070a12" metalness={0.3} roughness={0.85} />
            </mesh>
            <mesh position={[0, 0.01, 0]}>
              <cylinderGeometry args={[0.22, 0.22, 0.28, 20]} />
              <meshStandardMaterial color="#b9c0cc" metalness={1} roughness={0.25} />
            </mesh>
          </group>
        ))}
      </group>
    </group>
  );
}
