// The demo's one shared measurement: what is actually coming out of the
// speakers. All three options wire an AnalyserNode into their output and report
// the loudest frequency, so "the server's test tone survived the trip" is
// checkable the same way whatever the transport was.

export function dominantFrequency(analyser) {
  if (!analyser) return 0;
  const bins = new Float32Array(analyser.frequencyBinCount);
  analyser.getFloatFrequencyData(bins);
  let peak = 0;
  for (let i = 1; i < bins.length; i++) if (bins[i] > bins[peak]) peak = i;
  return (peak * analyser.context.sampleRate) / analyser.fftSize;
}
