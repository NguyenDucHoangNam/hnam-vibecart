self.addEventListener('push', function(event) {
  let data = { title: 'VibeCart', body: 'Bạn có thông báo mới', url: '/' };

  if (event.data) {
    try {
      data = event.data.json();
    } catch (e) {
      data.body = event.data.text();
    }
  }

  event.waitUntil(
    self.registration.showNotification(data.title || 'VibeCart', {
      body: data.body,
      icon: '/logo.png',
      badge: '/logo.png',
      tag: data.tag || 'vibecart-notification',
      data: { url: data.url || '/' },
      requireInteraction: false,
      silent: false
    })
  );
});

self.addEventListener('notificationclick', function(event) {
  event.notification.close();

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function(clientList) {
      for (const client of clientList) {
        if (client.url.includes(self.location.origin) && 'focus' in client) {
          client.navigate(event.notification.data.url);
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(event.notification.data.url);
      }
    })
  );
});
