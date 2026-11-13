import type { PersonalTraining } from '../../types'

/**
 * Whether a training can be ticked off yet — the same rule as PersonalTraining.hasStarted on the
 * server: an untimed entry counts from midnight of its day, a timed one from its hour. Local time, as
 * the dates themselves are: the client and the coach are in one timezone.
 */
export function hasTrainingStarted(
  training: Pick<PersonalTraining, 'date' | 'startTime'>,
  now: Date = new Date(),
): boolean {
  const [y, m, d] = training.date.split('-').map(Number)
  const [hh, mm] = (training.startTime ?? '00:00').split(':').map(Number)
  return now.getTime() >= new Date(y, m - 1, d, hh, mm).getTime()
}
