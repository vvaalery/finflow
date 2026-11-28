package com.finance.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public static BeanPostProcessor resilientCacheManagerPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof CacheManager manager && !(bean instanceof ResilientCacheManager)) {
                    return new ResilientCacheManager(manager);
                }
                return bean;
            }
        };
    }

    static class ResilientCacheManager implements CacheManager {
        private final CacheManager delegate;
        private final Map<String, Cache> caches = new ConcurrentHashMap<>();

        ResilientCacheManager(CacheManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public Cache getCache(String name) {
            Cache cache = delegate.getCache(name);
            return cache == null ? null : caches.computeIfAbsent(name, n -> new ResilientCache(cache));
        }

        @Override
        public Collection<String> getCacheNames() {
            return delegate.getCacheNames();
        }
    }

    static class ResilientCache implements Cache {
        private static final Logger log = LoggerFactory.getLogger(ResilientCache.class);

        private final Cache delegate;
        private final AtomicBoolean stale = new AtomicBoolean(false);

        ResilientCache(Cache delegate) {
            this.delegate = delegate;
        }

        private boolean usable() {
            if (!stale.get()) {
                return true;
            }
            try {
                delegate.clear();
                stale.set(false);
                log.info("Кеш {} очищен после восстановления Redis", delegate.getName());
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Object getNativeCache() {
            return delegate.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            if (!usable()) return null;
            try {
                return delegate.get(key);
            } catch (RuntimeException e) {
                log.warn("Redis недоступен при чтении кеша {}: {}; данные берутся из БД", getName(), e.getMessage());
                return null;
            }
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            if (!usable()) return null;
            try {
                return delegate.get(key, type);
            } catch (RuntimeException e) {
                log.warn("Redis недоступен при чтении кеша {}: {}", getName(), e.getMessage());
                return null;
            }
        }

        @Override
        public <T> T get(Object key, Callable<T> loader) {
            if (!usable()) return load(key, loader);
            try {
                return delegate.get(key, loader);
            } catch (ValueRetrievalException e) {
                throw e;
            } catch (RuntimeException e) {
                log.warn("Redis недоступен при чтении кеша {}: {}; данные берутся из БД", getName(), e.getMessage());
                return load(key, loader);
            }
        }

        private <T> T load(Object key, Callable<T> loader) {
            try {
                return loader.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new ValueRetrievalException(key, loader, e);
            }
        }

        @Override
        public void put(Object key, Object value) {
            if (stale.get()) return;
            try {
                delegate.put(key, value);
            } catch (RuntimeException e) {
                log.warn("Не удалось записать в кеш {}: {}", getName(), e.getMessage());
            }
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            if (stale.get()) return null;
            try {
                return delegate.putIfAbsent(key, value);
            } catch (RuntimeException e) {
                log.warn("Не удалось записать в кеш {}: {}", getName(), e.getMessage());
                return null;
            }
        }

        @Override
        public void evict(Object key) {
            try {
                delegate.evict(key);
            } catch (RuntimeException e) {
                markStale(e);
            }
        }

        @Override
        public boolean evictIfPresent(Object key) {
            try {
                return delegate.evictIfPresent(key);
            } catch (RuntimeException e) {
                markStale(e);
                return false;
            }
        }

        @Override
        public void clear() {
            try {
                delegate.clear();
            } catch (RuntimeException e) {
                markStale(e);
            }
        }

        @Override
        public boolean invalidate() {
            try {
                return delegate.invalidate();
            } catch (RuntimeException e) {
                markStale(e);
                return false;
            }
        }

        private void markStale(RuntimeException e) {
            stale.set(true);
            log.error("Не удалось очистить кеш {} ({}). Кеш отключён до успешной очистки, чтение — из БД",
                    getName(), e.getMessage());
        }
    }
}
