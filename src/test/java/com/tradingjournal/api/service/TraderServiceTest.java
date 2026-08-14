package com.tradingjournal.api.service;

import com.tradingjournal.api.dto.TraderRequest;
import com.tradingjournal.api.dto.TraderResponse;
import com.tradingjournal.api.exception.ResourceNotFoundException;
import com.tradingjournal.api.model.Trader;
import com.tradingjournal.api.repository.TraderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TraderServiceTest {

    @Mock
    private TraderRepository traderRepository;

    private TraderService traderService;

    private TraderRequest request;

    @BeforeEach
    void setUp() {
        traderService = new TraderService(traderRepository);
        request = new TraderRequest("Juan Pablo Ramirez", "juanpa28", "juanpa@example.com", new BigDecimal("5000.00"));
    }

    @Test
    void create_savesTraderWithRequestFields() {
        when(traderRepository.save(any(Trader.class))).thenAnswer(invocation -> {
            Trader saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        TraderResponse response = traderService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.fullName()).isEqualTo("Juan Pablo Ramirez");
        assertThat(response.username()).isEqualTo("juanpa28");
        assertThat(response.email()).isEqualTo("juanpa@example.com");
        assertThat(response.availableFunds()).isEqualByComparingTo("5000.00");

        ArgumentCaptor<Trader> captor = ArgumentCaptor.forClass(Trader.class);
        verify(traderRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("juanpa28");
    }

    @Test
    void findAll_mapsEveryTraderToResponse() {
        Trader trader1 = traderWithId(1L, "trader1");
        Trader trader2 = traderWithId(2L, "trader2");
        when(traderRepository.findAll()).thenReturn(List.of(trader1, trader2));

        List<TraderResponse> responses = traderService.findAll();

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(TraderResponse::username).containsExactly("trader1", "trader2");
    }

    @Test
    void findById_returnsResponse_whenTraderExists() {
        Trader trader = traderWithId(1L, "juanpa28");
        when(traderRepository.findById(1L)).thenReturn(Optional.of(trader));

        TraderResponse response = traderService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("juanpa28");
    }

    @Test
    void findById_throwsResourceNotFound_whenTraderMissing() {
        when(traderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> traderService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void update_overwritesFieldsOnExistingTrader() {
        Trader existing = traderWithId(1L, "oldUsername");
        when(traderRepository.findById(1L)).thenReturn(Optional.of(existing));
        TraderRequest updateRequest = new TraderRequest("New Name", "newUsername", "new@example.com", new BigDecimal("999.99"));

        TraderResponse response = traderService.update(1L, updateRequest);

        assertThat(response.username()).isEqualTo("newUsername");
        assertThat(response.fullName()).isEqualTo("New Name");
        assertThat(response.availableFunds()).isEqualByComparingTo("999.99");
    }

    @Test
    void update_throwsResourceNotFound_whenTraderMissing() {
        when(traderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> traderService.update(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesExistingTrader() {
        Trader existing = traderWithId(1L, "juanpa28");
        when(traderRepository.findById(1L)).thenReturn(Optional.of(existing));

        traderService.delete(1L);

        verify(traderRepository).delete(existing);
    }

    @Test
    void delete_throwsResourceNotFound_whenTraderMissing() {
        when(traderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> traderService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(traderRepository, never()).delete(any());
    }

    private Trader traderWithId(Long id, String username) {
        Trader trader = new Trader();
        trader.setId(id);
        trader.setUsername(username);
        trader.setFullName("Full " + username);
        trader.setEmail(username + "@example.com");
        trader.setAvailableFunds(new BigDecimal("1000.00"));
        return trader;
    }
}
