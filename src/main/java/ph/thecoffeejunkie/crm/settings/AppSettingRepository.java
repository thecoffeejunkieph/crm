package ph.thecoffeejunkie.crm.settings;

import org.springframework.data.jpa.repository.JpaRepository;

interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
