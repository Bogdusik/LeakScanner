export interface SseEvent {
  eventType: string;
  dataStr: string;
}

export const parseSseEvent = (raw: string): SseEvent => {
  let eventType = '';
  const data: string[] = [];

  for (const line of raw.split('\n')) {
    const separator = line.indexOf(':');
    if (separator <= 0) continue;

    const field = line.substring(0, separator);
    const value = line.substring(separator + 1).replace(/^ /, '');

    if (field === 'event') {
      eventType = value.trim();
    } else if (field === 'data') {
      data.push(value);
    }
  }

  return { eventType, dataStr: data.join('\n').trim() };
};
