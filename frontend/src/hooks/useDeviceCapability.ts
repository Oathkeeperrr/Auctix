import { useEffect, useState } from 'react';

export type QualityTier = 'high' | 'medium' | 'low' | 'none';

export interface DeviceCapability {
  tier: QualityTier;
  webgl: boolean;
  reducedMotion: boolean;
  isTouch: boolean;
  dpr: [number, number];
}

function detectWebGL(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    const canvas = document.createElement('canvas');
    return !!(
      window.WebGLRenderingContext &&
      (canvas.getContext('webgl2') || canvas.getContext('webgl'))
    );
  } catch {
    return false;
  }
}

/**
 * Decides how much 3D this device should be asked to draw.
 *
 * Phones get a simplified scene, machines that report few cores get fewer effects, and a user
 * who asked for reduced motion (or a browser without WebGL) gets the 2D presentation instead.
 */
export function useDeviceCapability(): DeviceCapability {
  const [capability, setCapability] = useState<DeviceCapability>(() => ({
    tier: 'high',
    webgl: true,
    reducedMotion: false,
    isTouch: false,
    dpr: [1, 2],
  }));

  useEffect(() => {
    const motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    const coarseQuery = window.matchMedia('(pointer: coarse)');
    const webgl = detectWebGL();

    const evaluate = () => {
      const reducedMotion = motionQuery.matches;
      const isTouch = coarseQuery.matches;
      const cores = navigator.hardwareConcurrency ?? 4;
      const narrow = window.innerWidth < 768;
      const memory = (navigator as Navigator & { deviceMemory?: number }).deviceMemory ?? 8;

      let tier: QualityTier = 'high';
      if (!webgl || reducedMotion) tier = 'none';
      else if (narrow || cores <= 4 || memory <= 4) tier = 'low';
      else if (window.innerWidth < 1280 || cores <= 8) tier = 'medium';

      setCapability({
        tier,
        webgl,
        reducedMotion,
        isTouch,
        dpr: tier === 'low' ? [1, 1.25] : tier === 'medium' ? [1, 1.5] : [1, 2],
      });
    };

    evaluate();
    motionQuery.addEventListener('change', evaluate);
    coarseQuery.addEventListener('change', evaluate);
    window.addEventListener('resize', evaluate);
    return () => {
      motionQuery.removeEventListener('change', evaluate);
      coarseQuery.removeEventListener('change', evaluate);
      window.removeEventListener('resize', evaluate);
    };
  }, []);

  return capability;
}
