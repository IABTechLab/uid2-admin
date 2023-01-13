package com.uid2.admin.store.version;

import com.uid2.admin.store.Clock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EpochVersionGeneratorTest {

    @Test
    void getVersion() {
        Clock clock = mock(Clock.class);
        when(clock.getEpochMillis()).thenReturn(500L);
        EpochVersionGenerator generator = new EpochVersionGenerator(clock);
        Long actual = generator.getVersion();
        assertEquals(500L, actual);
    }
}