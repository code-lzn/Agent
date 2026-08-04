package com.limou.agent.service.impl;

import com.limou.agent.exception.BusinessException;
import com.limou.agent.mapper.ScheduleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleServiceImplTest {

    @Test
    void supportsCinemaOnlyScheduleQuery() {
        ScheduleMapper mapper = mock(ScheduleMapper.class);
        ScheduleServiceImpl service = new ScheduleServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        when(mapper.selectListByQuery(any())).thenReturn(Collections.emptyList());

        assertThat(service.queryScheduleList(null, 12L, null)).isEmpty();

        verify(mapper).selectListByQuery(any());
    }

    @Test
    void rejectsScheduleQueryWithoutFilmOrCinema() {
        ScheduleServiceImpl service = new ScheduleServiceImpl();

        assertThatThrownBy(() -> service.queryScheduleList(null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能同时为空");
    }
}
