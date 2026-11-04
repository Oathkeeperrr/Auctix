import { RoundedBox } from '@react-three/drei';
import { useMemo } from 'react';

/**
 * A generic high-top sneaker: outsole, midsole, upper, toe cap, collar and laces.
 * Deliberately an original silhouette rather than any particular brand's design.
 */
export function SneakerModel({ accent = '#ff5f6d' }: { accent?: string }) {
  const laces = useMemo(() => [0.1, 0.34, 0.58, 0.82], []);

  return (
    <group rotation={[0, -0.6, 0.04]} scale={1.05}>
      {/* Outsole */}
      <RoundedBox args={[2.5, 0.16, 1.0]} radius={0.08} smoothness={4} position={[0, -0.62, 0]} castShadow>
        <meshStandardMaterial color="#0d1119" roughness={0.9} metalness={0.05} />
      </RoundedBox>

      {/* Midsole */}
      <RoundedBox args={[2.44, 0.3, 0.96]} radius={0.14} smoothness={4} position={[0, -0.42, 0]} castShadow>
        <meshStandardMaterial color="#f2f0ea" roughness={0.65} metalness={0.05} />
      </RoundedBox>

      {/* Main upper */}
      <RoundedBox args={[2.0, 0.66, 0.9]} radius={0.22} smoothness={5} position={[-0.1, -0.02, 0]} castShadow>
        <meshStandardMaterial color="#f5f3ee" roughness={0.72} metalness={0.04} />
      </RoundedBox>

      {/* Toe cap */}
      <RoundedBox args={[0.72, 0.5, 0.88]} radius={0.22} smoothness={5} position={[0.84, -0.14, 0]} castShadow>
        <meshStandardMaterial color={accent} roughness={0.6} metalness={0.08} />
      </RoundedBox>

      {/* Heel counter */}
      <RoundedBox args={[0.6, 0.92, 0.9]} radius={0.2} smoothness={5} position={[-1.0, 0.16, 0]} castShadow>
        <meshStandardMaterial color={accent} roughness={0.6} metalness={0.08} />
      </RoundedBox>

      {/* Ankle collar */}
      <mesh position={[-0.86, 0.62, 0]} rotation={[Math.PI / 2, 0, 0]} castShadow>
        <torusGeometry args={[0.36, 0.13, 16, 36]} />
        <meshStandardMaterial color="#1a2032" roughness={0.85} />
      </mesh>

      {/* Side panel */}
      <RoundedBox args={[0.9, 0.3, 0.06]} radius={0.06} smoothness={4} position={[-0.1, -0.12, 0.46]}>
        <meshStandardMaterial color="#121828" roughness={0.6} metalness={0.2} />
      </RoundedBox>
      <RoundedBox args={[0.9, 0.3, 0.06]} radius={0.06} smoothness={4} position={[-0.1, -0.12, -0.46]}>
        <meshStandardMaterial color="#121828" roughness={0.6} metalness={0.2} />
      </RoundedBox>

      {/* Tongue */}
      <RoundedBox args={[0.7, 0.52, 0.56]} radius={0.14} smoothness={4} position={[-0.18, 0.4, 0]}>
        <meshStandardMaterial color="#e7e4dc" roughness={0.78} />
      </RoundedBox>

      {/* Laces */}
      {laces.map((offset) => (
        <mesh
          key={offset}
          position={[0.14 - offset * 0.78, 0.36 - offset * 0.1, 0]}
          /* A cylinder runs along Y by default; turn it so the lace crosses the shoe. */
          rotation={[Math.PI / 2, 0, 0]}
        >
          <cylinderGeometry args={[0.035, 0.035, 0.66, 12]} />
          <meshStandardMaterial color="#cfd4de" roughness={0.9} />
        </mesh>
      ))}

      {/* Heel tab */}
      <mesh position={[-1.3, 0.56, 0]}>
        <boxGeometry args={[0.1, 0.24, 0.34]} />
        <meshStandardMaterial color="#d8b35f" metalness={0.6} roughness={0.35} />
      </mesh>
    </group>
  );
}
