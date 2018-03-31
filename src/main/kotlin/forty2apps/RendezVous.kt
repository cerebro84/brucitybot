package forty2apps

import java.time.LocalDate

class RendezVous internal constructor(internal val rdvDate: LocalDate?, internal val newPossibleDate: LocalDate?) {

    internal fun newDateIsBetter(): Boolean {
        return rdvDate != null && newPossibleDate != null && newPossibleDate.isBefore(rdvDate)
    }
}
